package com.juryopt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.juryopt.orchestrator.domain.DefenseRequest;
import com.juryopt.orchestrator.domain.DefenseStatus;
import com.juryopt.orchestrator.dto.DefenseRequestDto;
import com.juryopt.orchestrator.dto.DefenseResponseDto;
import com.juryopt.orchestrator.exception.BusinessRuleException;
import com.juryopt.orchestrator.exception.DuplicateResourceException;
import com.juryopt.orchestrator.repository.DefenseRequestRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefenseServiceTest {

    @Mock
    private DefenseRequestRepository defenseRepository;

    @InjectMocks
    private DefenseService defenseService;

    @Test
    void create_shouldForceProvisionalStatusAndNormalizeBlankName() {
        DefenseRequestDto dto = new DefenseRequestDto(
                "ETU-2026-001", "   ", "Plateforme SOA JuryOpt",
                "PROF-01", "PROF-02", "PROF-03"
        );
        when(defenseRepository.existsByStudentMatricule("ETU-2026-001")).thenReturn(false);
        when(defenseRepository.save(any(DefenseRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DefenseResponseDto result = defenseService.create(dto);

        assertThat(result.status()).isEqualTo(DefenseStatus.PROVISIONAL_PENDING_CLEARANCE);
        assertThat(result.studentName()).isNull();
        assertThat(result.clearanceToken()).isNull();
    }

    @Test
    void create_shouldRejectNonDistinctJuryMembers() {
        DefenseRequestDto dto = new DefenseRequestDto(
                "ETU-2026-001", "Amine Ben Salem", "Plateforme SOA JuryOpt",
                "PROF-01", "PROF-01", "PROF-03"
        );

        assertThatThrownBy(() -> defenseService.create(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("distincts");

        verify(defenseRepository, never()).save(any());
    }

    @Test
    void create_shouldRejectDuplicateMatricule() {
        DefenseRequestDto dto = new DefenseRequestDto(
                "ETU-2026-001", "Amine Ben Salem", "Plateforme SOA JuryOpt",
                "PROF-01", "PROF-02", "PROF-03"
        );
        when(defenseRepository.existsByStudentMatricule("ETU-2026-001")).thenReturn(true);

        assertThatThrownBy(() -> defenseService.create(dto))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ETU-2026-001");

        verify(defenseRepository, never()).save(any());
    }

    @Test
    void update_shouldRejectWhenDefenseIsAlreadyScheduled() {
        DefenseRequest existing = new DefenseRequest(
                "ETU-2026-001", "Amine Ben Salem", "Ancien titre",
                "PROF-01", "PROF-02", "PROF-03", DefenseStatus.SCHEDULED
        );
        DefenseRequestDto dto = new DefenseRequestDto(
                "ETU-2026-001", "Amine Ben Salem", "Nouveau titre",
                "PROF-01", "PROF-02", "PROF-04"
        );
        when(defenseRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> defenseService.update(1L, dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SCHEDULED");
    }

    @Test
    void delete_shouldRejectWhenDefenseIsPublished() {
        DefenseRequest existing = new DefenseRequest(
                "ETU-2026-001", "Amine Ben Salem", "Titre PFE",
                "PROF-01", "PROF-02", "PROF-03", DefenseStatus.PUBLISHED
        );
        when(defenseRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> defenseService.delete(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PUBLISHED");

        verify(defenseRepository, never()).delete(any());
    }
}