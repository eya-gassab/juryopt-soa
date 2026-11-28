package com.juryopt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.juryopt.orchestrator.domain.DefenseRequest;
import com.juryopt.orchestrator.domain.DefenseSlot;
import com.juryopt.orchestrator.domain.DefenseStatus;
import com.juryopt.orchestrator.repository.DefenseRequestRepository;
import com.juryopt.orchestrator.repository.DefenseScheduleRepository;
import com.juryopt.orchestrator.repository.DefenseSlotRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchedulePersistenceServiceTest {

    @Mock
    private DefenseRequestRepository defenseRepository;

    @Mock
    private DefenseScheduleRepository scheduleRepository;

    @Mock
    private DefenseSlotRepository slotRepository;

    @InjectMocks
    private SchedulePersistenceService persistenceService;

    @Test
    void markDefensePublishedAndReserveSlot_shouldPublishDefenseAndMarkSlotUnavailable() {
        DefenseRequest defense = new DefenseRequest(
                "ETU-2026-001", "Amine Ben Salem", "Sujet PFE",
                "PROF-01", "PROF-02", "PROF-03", DefenseStatus.SCHEDULED
        );
        defense.setAssignedSlotCode("2026-06-15_09:00");
        defense.setAssignedRoomCode("SALLE-A1");

        DefenseSlot slot = new DefenseSlot("2026-06-15_09:00", "SALLE-A1", true);

        when(defenseRepository.save(any(DefenseRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(slotRepository.findBySlotCodeAndRoomCode("2026-06-15_09:00", "SALLE-A1"))
                .thenReturn(Optional.of(slot));

        DefenseRequest result = persistenceService.markDefensePublishedAndReserveSlot(defense, "REG-2026-0001");

        assertThat(result.getStatus()).isEqualTo(DefenseStatus.PUBLISHED);
        assertThat(result.getRegistryCode()).isEqualTo("REG-2026-0001");
        assertThat(slot.isAvailable()).isFalse();
        verify(defenseRepository).save(defense);
        verify(slotRepository).save(slot);
    }

    @Test
    void markDefensePublishedAndReserveSlot_whenSlotAlreadyUnavailable_shouldNotSaveSlotAgain() {
        DefenseRequest defense = new DefenseRequest(
                "ETU-2026-001", "Amine Ben Salem", "Sujet PFE",
                "PROF-01", "PROF-02", "PROF-03", DefenseStatus.SCHEDULED
        );
        defense.setAssignedSlotCode("2026-06-15_09:00");
        defense.setAssignedRoomCode("SALLE-A1");

        DefenseSlot slot = new DefenseSlot("2026-06-15_09:00", "SALLE-A1", false);

        when(defenseRepository.save(any(DefenseRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(slotRepository.findBySlotCodeAndRoomCode("2026-06-15_09:00", "SALLE-A1"))
                .thenReturn(Optional.of(slot));

        DefenseRequest result = persistenceService.markDefensePublishedAndReserveSlot(defense, "REG-2026-0001");

        assertThat(result.getStatus()).isEqualTo(DefenseStatus.PUBLISHED);
        assertThat(slot.isAvailable()).isFalse();
        verify(slotRepository, never()).save(any());
    }
}