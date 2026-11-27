package com.juryopt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.juryopt.orchestrator.client.OptimizerGrpcClient;
import com.juryopt.orchestrator.client.OptimizerGrpcClient.OptimizationResult;
import com.juryopt.orchestrator.client.RegistrarSoapClient;
import com.juryopt.orchestrator.client.RegistrarSoapClient.RegistrationResult;
import com.juryopt.orchestrator.domain.DefenseRequest;
import com.juryopt.orchestrator.domain.DefenseSchedule;
import com.juryopt.orchestrator.domain.DefenseSlot;
import com.juryopt.orchestrator.domain.DefenseStatus;
import com.juryopt.orchestrator.domain.ScheduleStatus;
import com.juryopt.orchestrator.dto.OptimizationRunResponseDto;
import com.juryopt.orchestrator.dto.ProposedAssignmentDto;
import com.juryopt.orchestrator.dto.SchedulePublicationResponseDto;
import com.juryopt.orchestrator.exception.BusinessRuleException;
import com.juryopt.orchestrator.exception.DuplicateRegistrationFaultException;
import com.juryopt.orchestrator.exception.ExternalServiceUnavailableException;
import com.juryopt.orchestrator.repository.DefenseRequestRepository;
import com.juryopt.orchestrator.repository.DefenseScheduleRepository;
import com.juryopt.orchestrator.repository.DefenseSlotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScheduleWorkflowServiceTest {

    @Mock
    private DefenseRequestRepository defenseRepository;

    @Mock
    private DefenseSlotRepository slotRepository;

    @Mock
    private DefenseScheduleRepository scheduleRepository;

    @Mock
    private SchedulePersistenceService persistenceService;

    @Mock
    private OptimizerGrpcClient optimizerGrpcClient;

    @Mock
    private RegistrarSoapClient registrarSoapClient;

    @InjectMocks
    private ScheduleWorkflowService workflowService;

    @Test
    void runOptimization_whenFallbackUsed_shouldPersistUnoptimizedFallbackStatus() {
        DefenseRequest defense = buildDefense(1L, "ETU-2026-001", DefenseStatus.ELIGIBLE, "CLR-11111111");
        DefenseSlot slot = new DefenseSlot("2026-06-15_09:00", "SALLE-A1", true);

        when(defenseRepository.findByStatusInOrderByIdAsc(List.of(DefenseStatus.ELIGIBLE, DefenseStatus.SCHEDULED)))
                .thenReturn(List.of(defense));
        when(slotRepository.findByAvailableTrueOrderByIdAsc()).thenReturn(List.of(slot));

        OptimizationResult fallbackResult = new OptimizationResult(
                true, 1, 0, 0.0, List.of(),
                List.of(new ProposedAssignmentDto("1", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-02", "PROF-03"))
        );
        when(optimizerGrpcClient.optimizeSchedule(any(), any())).thenReturn(fallbackResult);

        DefenseSchedule savedSchedule = buildSchedule(10L, ScheduleStatus.UNOPTIMIZED_FALLBACK, 1, 0);
        when(persistenceService.saveOptimizationResult(
                any(), any(), eq(ScheduleStatus.UNOPTIMIZED_FALLBACK), anyInt(), anyInt(), anyDouble()))
                .thenReturn(savedSchedule);

        OptimizationRunResponseDto response = workflowService.runOptimization();

        assertThat(response.status()).isEqualTo(ScheduleStatus.UNOPTIMIZED_FALLBACK);
        assertThat(response.fallbackUsed()).isTrue();
    }

    @Test
    void runOptimization_whenOnlyProvisionalDefensesExist_shouldThrow422() {
        when(defenseRepository.findByStatusInOrderByIdAsc(List.of(DefenseStatus.ELIGIBLE, DefenseStatus.SCHEDULED)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> workflowService.runOptimization())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PROVISIONAL_PENDING_CLEARANCE");

        verifyNoInteractions(optimizerGrpcClient);
    }

    @Test
    void publishSchedule_shouldRejectUnoptimizedFallbackSchedule() {
        DefenseSchedule fallback = buildSchedule(2L, ScheduleStatus.UNOPTIMIZED_FALLBACK, 2, 0);
        when(scheduleRepository.findById(2L)).thenReturn(Optional.of(fallback));

        assertThatThrownBy(() -> workflowService.publishSchedule(2L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("UNOPTIMIZED_FALLBACK");

        verifyNoInteractions(registrarSoapClient);
    }

    @Test
    void publishSchedule_shouldRejectDraftWithConflicts() {
        DefenseSchedule conflicted = buildSchedule(3L, ScheduleStatus.DRAFT, 2, 1);
        when(scheduleRepository.findById(3L)).thenReturn(Optional.of(conflicted));
        when(scheduleRepository.findTopByOrderByIdDesc()).thenReturn(Optional.of(conflicted));

        assertThatThrownBy(() -> workflowService.publishSchedule(3L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("conflit");

        verifyNoInteractions(registrarSoapClient);
    }

    @Test
    void publishSchedule_shouldRejectOlderDraftSchedule() {
        DefenseSchedule older = buildSchedule(1L, ScheduleStatus.DRAFT, 2, 0);
        DefenseSchedule latest = buildSchedule(2L, ScheduleStatus.DRAFT, 2, 0);
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(older));
        when(scheduleRepository.findTopByOrderByIdDesc()).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> workflowService.publishSchedule(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dernier planning");

        verifyNoInteractions(registrarSoapClient);
    }

    @Test
    void publishSchedule_shouldAcceptMatchingDuplicateFaultAndMarkSchedulePublished() {
        DefenseSchedule draft = buildSchedule(4L, ScheduleStatus.DRAFT, 2, 0);
        DefenseRequest d1 = buildScheduledDefense(1L, "ETU-2026-001", "CLR-11111111");
        DefenseRequest d2 = buildScheduledDefense(2L, "ETU-2026-002", "CLR-22222222");

        when(scheduleRepository.findById(4L)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findTopByOrderByIdDesc()).thenReturn(Optional.of(draft));
        when(defenseRepository.findByStatusOrderByIdAsc(DefenseStatus.SCHEDULED)).thenReturn(List.of(d1, d2));

        when(registrarSoapClient.registerOfficialDefense(eq("ETU-2026-001"), any(), any(), any(), any()))
                .thenThrow(new DuplicateRegistrationFaultException("ETU-2026-001", "REG-2026-0001", "Déjà enregistrée"));
        when(registrarSoapClient.registerOfficialDefense(eq("ETU-2026-002"), any(), any(), any(), any()))
                .thenReturn(new RegistrationResult("REG-2026-0002", "2026-11-26T21:00:00"));

        SchedulePublicationResponseDto result = workflowService.publishSchedule(4L);

        assertThat(result.scheduleStatus()).isEqualTo(ScheduleStatus.PUBLISHED);
        assertThat(result.publishedCount()).isEqualTo(2);
        assertThat(d1.getRegistryCode()).isEqualTo("REG-2026-0001");
        assertThat(d1.getStatus()).isEqualTo(DefenseStatus.PUBLISHED);
        assertThat(d2.getRegistryCode()).isEqualTo("REG-2026-0002");
        assertThat(d2.getStatus()).isEqualTo(DefenseStatus.PUBLISHED);
        verify(defenseRepository, times(2)).save(any(DefenseRequest.class));
        verify(scheduleRepository).save(draft);
    }

    @Test
    void publishSchedule_whenSoapFailsMidway_shouldPersistFirstAndKeepScheduleDraft() {
        DefenseSchedule draft = buildSchedule(5L, ScheduleStatus.DRAFT, 2, 0);
        DefenseRequest d1 = buildScheduledDefense(1L, "ETU-2026-001", "CLR-11111111");
        DefenseRequest d2 = buildScheduledDefense(2L, "ETU-2026-002", "CLR-22222222");

        when(scheduleRepository.findById(5L)).thenReturn(Optional.of(draft));
        when(scheduleRepository.findTopByOrderByIdDesc()).thenReturn(Optional.of(draft));
        when(defenseRepository.findByStatusOrderByIdAsc(DefenseStatus.SCHEDULED)).thenReturn(List.of(d1, d2));

        when(registrarSoapClient.registerOfficialDefense(eq("ETU-2026-001"), any(), any(), any(), any()))
                .thenReturn(new RegistrationResult("REG-2026-0001", "2026-11-26T21:00:00"));
        when(registrarSoapClient.registerOfficialDefense(eq("ETU-2026-002"), any(), any(), any(), any()))
                .thenThrow(new ExternalServiceUnavailableException("Service SOAP indisponible"));

        assertThatThrownBy(() -> workflowService.publishSchedule(5L))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessageContaining("1 soutenance(s)");

        assertThat(d1.getStatus()).isEqualTo(DefenseStatus.PUBLISHED);
        assertThat(d2.getStatus()).isEqualTo(DefenseStatus.SCHEDULED);
        assertThat(draft.getStatus()).isEqualTo(ScheduleStatus.DRAFT);
        verify(defenseRepository, times(1)).save(d1);
        verify(scheduleRepository, never()).save(any());
    }

    private DefenseRequest buildDefense(Long id, String matricule, DefenseStatus status, String token) {
        DefenseRequest d = new DefenseRequest(
                matricule, "Étudiant Test", "Sujet PFE",
                "PROF-01", "PROF-02", "PROF-03", status
        );
        d.setClearanceToken(token);
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    private DefenseRequest buildScheduledDefense(Long id, String matricule, String token) {
        DefenseRequest d = buildDefense(id, matricule, DefenseStatus.SCHEDULED, token);
        d.setAssignedSlotCode("2026-06-15_09:00");
        d.setAssignedRoomCode("SALLE-A1");
        return d;
    }

    private DefenseSchedule buildSchedule(Long id, ScheduleStatus status, int assigned, int conflicts) {
        DefenseSchedule schedule = new DefenseSchedule(LocalDateTime.now(), status, assigned, conflicts, 0.0);
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }
}