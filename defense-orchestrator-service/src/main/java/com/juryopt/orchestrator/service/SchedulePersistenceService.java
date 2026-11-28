package com.juryopt.orchestrator.service;

import com.juryopt.orchestrator.domain.DefenseRequest;
import com.juryopt.orchestrator.domain.DefenseSchedule;
import com.juryopt.orchestrator.domain.DefenseStatus;
import com.juryopt.orchestrator.domain.ScheduleStatus;
import com.juryopt.orchestrator.dto.ProposedAssignmentDto;
import com.juryopt.orchestrator.repository.DefenseRequestRepository;
import com.juryopt.orchestrator.repository.DefenseScheduleRepository;
import com.juryopt.orchestrator.repository.DefenseSlotRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulePersistenceService {

    private final DefenseRequestRepository defenseRepository;
    private final DefenseScheduleRepository scheduleRepository;
    private final DefenseSlotRepository slotRepository;

    public SchedulePersistenceService(DefenseRequestRepository defenseRepository,
                                      DefenseScheduleRepository scheduleRepository,
                                      DefenseSlotRepository slotRepository) {
        this.defenseRepository = defenseRepository;
        this.scheduleRepository = scheduleRepository;
        this.slotRepository = slotRepository;
    }

    @Transactional
    public DefenseSchedule saveOptimizationResult(List<DefenseRequest> defenses,
                                                  List<ProposedAssignmentDto> assignments,
                                                  ScheduleStatus status,
                                                  int totalAssigned,
                                                  int conflictCount,
                                                  double balanceScore) {
        Map<String, DefenseRequest> byId = defenses.stream()
                .collect(Collectors.toMap(d -> String.valueOf(d.getId()), Function.identity()));

        for (ProposedAssignmentDto assignment : assignments) {
            DefenseRequest defense = byId.get(assignment.requestId());
            if (defense != null) {
                defense.setAssignedSlotCode(assignment.slotCode());
                defense.setAssignedRoomCode(assignment.roomCode());
                defense.setStatus(DefenseStatus.SCHEDULED);
            }
        }
        defenseRepository.saveAll(defenses);

        DefenseSchedule schedule = new DefenseSchedule(
                LocalDateTime.now(),
                status,
                totalAssigned,
                conflictCount,
                balanceScore
        );
        return scheduleRepository.save(schedule);
    }

    @Transactional
    public DefenseRequest markDefensePublishedAndReserveSlot(DefenseRequest defense, String registryCode) {
        defense.setRegistryCode(registryCode);
        defense.setStatus(DefenseStatus.PUBLISHED);
        DefenseRequest saved = defenseRepository.save(defense);

        if (saved.getAssignedSlotCode() != null && saved.getAssignedRoomCode() != null) {
            slotRepository.findBySlotCodeAndRoomCode(saved.getAssignedSlotCode(), saved.getAssignedRoomCode())
                    .ifPresent(slot -> {
                        if (slot.isAvailable()) {
                            slot.setAvailable(false);
                            slotRepository.save(slot);
                        }
                    });
        }
        return saved;
    }
}