package com.juryopt.orchestrator.service;

import com.juryopt.orchestrator.client.OptimizerGrpcClient;
import com.juryopt.orchestrator.client.OptimizerGrpcClient.DefenseInput;
import com.juryopt.orchestrator.client.OptimizerGrpcClient.OptimizationResult;
import com.juryopt.orchestrator.client.OptimizerGrpcClient.SlotInput;
import com.juryopt.orchestrator.client.RegistrarSoapClient;
import com.juryopt.orchestrator.client.RegistrarSoapClient.RegistrationResult;
import com.juryopt.orchestrator.domain.DefenseRequest;
import com.juryopt.orchestrator.domain.DefenseSchedule;
import com.juryopt.orchestrator.domain.DefenseSlot;
import com.juryopt.orchestrator.domain.DefenseStatus;
import com.juryopt.orchestrator.domain.ScheduleStatus;
import com.juryopt.orchestrator.dto.ConflictDto;
import com.juryopt.orchestrator.dto.OptimizationRunResponseDto;
import com.juryopt.orchestrator.dto.PublishedDefenseDto;
import com.juryopt.orchestrator.dto.ScheduleEvaluationRequestDto;
import com.juryopt.orchestrator.dto.ScheduleEvaluationResponseDto;
import com.juryopt.orchestrator.dto.SchedulePublicationResponseDto;
import com.juryopt.orchestrator.exception.BusinessRuleException;
import com.juryopt.orchestrator.exception.DuplicateRegistrationFaultException;
import com.juryopt.orchestrator.exception.DuplicateResourceException;
import com.juryopt.orchestrator.exception.ExternalServiceUnavailableException;
import com.juryopt.orchestrator.exception.ResourceNotFoundException;
import com.juryopt.orchestrator.repository.DefenseRequestRepository;
import com.juryopt.orchestrator.repository.DefenseScheduleRepository;
import com.juryopt.orchestrator.repository.DefenseSlotRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScheduleWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleWorkflowService.class);

    private final DefenseRequestRepository defenseRepository;
    private final DefenseSlotRepository slotRepository;
    private final DefenseScheduleRepository scheduleRepository;
    private final SchedulePersistenceService persistenceService;
    private final OptimizerGrpcClient optimizerGrpcClient;
    private final RegistrarSoapClient registrarSoapClient;

    public ScheduleWorkflowService(DefenseRequestRepository defenseRepository,
                                   DefenseSlotRepository slotRepository,
                                   DefenseScheduleRepository scheduleRepository,
                                   SchedulePersistenceService persistenceService,
                                   OptimizerGrpcClient optimizerGrpcClient,
                                   RegistrarSoapClient registrarSoapClient) {
        this.defenseRepository = defenseRepository;
        this.slotRepository = slotRepository;
        this.scheduleRepository = scheduleRepository;
        this.persistenceService = persistenceService;
        this.optimizerGrpcClient = optimizerGrpcClient;
        this.registrarSoapClient = registrarSoapClient;
    }

    public OptimizationRunResponseDto runOptimization() {
        List<DefenseRequest> eligibleDefenses = defenseRepository.findByStatusInOrderByIdAsc(
                List.of(DefenseStatus.ELIGIBLE, DefenseStatus.SCHEDULED)
        );
        if (eligibleDefenses.isEmpty()) {
            throw new BusinessRuleException(
                    "Aucune demande éligible à optimiser (les demandes PROVISIONAL_PENDING_CLEARANCE sont exclues)."
            );
        }

        List<DefenseSlot> availableSlots = slotRepository.findByAvailableTrueOrderByIdAsc();
        if (availableSlots.isEmpty()) {
            throw new BusinessRuleException("Aucun créneau disponible pour lancer l'optimisation.");
        }

        List<DefenseInput> defenseInputs = eligibleDefenses.stream()
                .map(d -> new DefenseInput(
                        String.valueOf(d.getId()),
                        d.getSupervisorCode(),
                        d.getPresidentCode(),
                        d.getRapporteurCode()
                ))
                .toList();

        List<SlotInput> slotInputs = availableSlots.stream()
                .map(s -> new SlotInput(s.getSlotCode(), s.getRoomCode()))
                .toList();

        OptimizationResult result = optimizerGrpcClient.optimizeSchedule(defenseInputs, slotInputs);

        ScheduleStatus scheduleStatus = result.fallbackUsed()
                ? ScheduleStatus.UNOPTIMIZED_FALLBACK
                : ScheduleStatus.DRAFT;

        DefenseSchedule savedSchedule = persistenceService.saveOptimizationResult(
                eligibleDefenses,
                result.assignments(),
                scheduleStatus,
                result.assignedCount(),
                result.conflictCount(),
                result.balanceScore()
        );

        return new OptimizationRunResponseDto(
                savedSchedule.getId(),
                savedSchedule.getCreatedAt(),
                savedSchedule.getStatus(),
                result.fallbackUsed(),
                savedSchedule.getTotalAssigned(),
                savedSchedule.getConflictCount(),
                savedSchedule.getBalanceScore(),
                result.steps(),
                result.assignments()
        );
    }

    public ScheduleEvaluationResponseDto evaluateSchedule(ScheduleEvaluationRequestDto request) {
        List<ConflictDto> conflicts = optimizerGrpcClient.evaluateSchedule(request.assignments());
        return new ScheduleEvaluationResponseDto(conflicts.isEmpty(), conflicts.size(), conflicts);
    }

    public SchedulePublicationResponseDto publishSchedule(Long scheduleId) {
        DefenseSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Planning introuvable avec l'id " + scheduleId
                ));

        validatePublishable(schedule);

        List<DefenseRequest> pendingDefenses = defenseRepository.findByStatusOrderByIdAsc(DefenseStatus.SCHEDULED);
        if (pendingDefenses.isEmpty()) {
            List<DefenseRequest> alreadyPublished = defenseRepository.findByStatusOrderByIdAsc(DefenseStatus.PUBLISHED);
            if (alreadyPublished.isEmpty()) {
                throw new BusinessRuleException("Aucune soutenance planifiée à publier pour le planning " + scheduleId + ".");
            }
            schedule.setStatus(ScheduleStatus.PUBLISHED);
            scheduleRepository.save(schedule);
            List<PublishedDefenseDto> items = alreadyPublished.stream()
                    .map(d -> new PublishedDefenseDto(
                            d.getId(), d.getStudentMatricule(), d.getRegistryCode(), d.getStatus(), true))
                    .toList();
            return new SchedulePublicationResponseDto(schedule.getId(), schedule.getStatus(), items.size(), items);
        }

        for (DefenseRequest defense : pendingDefenses) {
            if (defense.getClearanceToken() == null || defense.getClearanceToken().isBlank()
                    || defense.getAssignedSlotCode() == null || defense.getAssignedRoomCode() == null) {
                throw new BusinessRuleException(
                        "La demande " + defense.getId() + " ne dispose pas de toutes les informations requises pour la publication."
                );
            }
        }

        List<PublishedDefenseDto> publishedItems = new ArrayList<>();
        int newlySavedCount = 0;

        for (DefenseRequest defense : pendingDefenses) {
            String jurySummary = "Encadrant=" + defense.getSupervisorCode()
                    + ", President=" + defense.getPresidentCode()
                    + ", Rapporteur=" + defense.getRapporteurCode();

            try {
                RegistrationResult registration = registrarSoapClient.registerOfficialDefense(
                        defense.getStudentMatricule(),
                        defense.getClearanceToken(),
                        defense.getAssignedSlotCode(),
                        defense.getAssignedRoomCode(),
                        jurySummary
                );
                DefenseRequest published = persistenceService.markDefensePublishedAndReserveSlot(
                        defense,
                        registration.registryCode()
                );
                newlySavedCount++;
                publishedItems.add(new PublishedDefenseDto(
                        published.getId(),
                        published.getStudentMatricule(),
                        published.getRegistryCode(),
                        published.getStatus(),
                        false
                ));
            } catch (DuplicateRegistrationFaultException dup) {
                if (matchesDefense(defense, dup)) {
                    log.info("Doublon SOAP cohérent pour {} (code existant {}) -> marqué PUBLISHED",
                            defense.getStudentMatricule(), dup.getExistingRegistryCode());
                    DefenseRequest published = persistenceService.markDefensePublishedAndReserveSlot(
                            defense,
                            dup.getExistingRegistryCode()
                    );
                    newlySavedCount++;
                    publishedItems.add(new PublishedDefenseDto(
                            published.getId(),
                            published.getStudentMatricule(),
                            published.getRegistryCode(),
                            published.getStatus(),
                            true
                    ));
                } else {
                    throw new DuplicateResourceException(
                            "Incohérence de doublon SOAP pour le matricule " + defense.getStudentMatricule()
                                    + " : " + dup.getMessage()
                    );
                }
            } catch (ExternalServiceUnavailableException ex) {
                throw new ExternalServiceUnavailableException(
                        "Publication interrompue (" + newlySavedCount + " soutenance(s) déjà enregistrée(s)) : "
                                + ex.getMessage(),
                        ex
                );
            }
        }

        schedule.setStatus(ScheduleStatus.PUBLISHED);
        scheduleRepository.save(schedule);

        return new SchedulePublicationResponseDto(
                schedule.getId(),
                schedule.getStatus(),
                publishedItems.size(),
                publishedItems
        );
    }

    private void validatePublishable(DefenseSchedule schedule) {
        if (schedule.getStatus() == ScheduleStatus.PUBLISHED) {
            throw new BusinessRuleException("Le planning " + schedule.getId() + " est déjà publié.");
        }
        if (schedule.getStatus() == ScheduleStatus.UNOPTIMIZED_FALLBACK) {
            throw new BusinessRuleException(
                    "Un planning UNOPTIMIZED_FALLBACK ne peut pas être publié. Relancez une optimisation."
            );
        }
        if (schedule.getStatus() != ScheduleStatus.DRAFT) {
            throw new BusinessRuleException(
                    "Seul un planning au statut DRAFT peut être publié (statut actuel : " + schedule.getStatus() + ")."
            );
        }

        DefenseSchedule latest = scheduleRepository.findTopByOrderByIdDesc().orElse(schedule);
        if (!latest.getId().equals(schedule.getId())) {
            throw new BusinessRuleException(
                    "Seul le dernier planning généré (id=" + latest.getId() + ") peut être publié."
            );
        }

        if (schedule.getConflictCount() > 0) {
            throw new BusinessRuleException(
                    "Impossible de publier un planning contenant " + schedule.getConflictCount() + " conflit(s)."
            );
        }
    }

    private boolean matchesDefense(DefenseRequest defense, DuplicateRegistrationFaultException dup) {
        boolean matriculeMatches = defense.getStudentMatricule().equals(dup.getStudentMatricule());
        boolean codePresent = dup.getExistingRegistryCode() != null && !dup.getExistingRegistryCode().isBlank();
        boolean codeConsistent = defense.getRegistryCode() == null
                || defense.getRegistryCode().equals(dup.getExistingRegistryCode());
        return matriculeMatches && codePresent && codeConsistent;
    }
}