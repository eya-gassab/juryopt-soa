package com.juryopt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.juryopt.orchestrator.client.RegistrarSoapClient;
import com.juryopt.orchestrator.client.RegistrarSoapClient.ClearanceResult;
import com.juryopt.orchestrator.domain.DefenseRequest;
import com.juryopt.orchestrator.domain.DefenseStatus;
import com.juryopt.orchestrator.dto.DefenseRequestDto;
import com.juryopt.orchestrator.dto.DefenseResponseDto;
import com.juryopt.orchestrator.exception.BusinessRuleException;
import com.juryopt.orchestrator.exception.DuplicateResourceException;
import com.juryopt.orchestrator.exception.ExternalServiceUnavailableException;
import com.juryopt.orchestrator.exception.ResourceNotFoundException;
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

    @Mock
    private RegistrarSoapClient registrarSoapClient;

    @InjectMocks
    private DefenseService defenseService;

    @Test
    void create_shouldFallbackToProvisionalStatusAndNormalizeBlankNameWhenSoapUnavailable() {
        DefenseRequestDto dto = new DefenseRequestDto(
                "ETU-2026-001", "   ", "Plateforme SOA JuryOpt",
                "PROF-01", "PROF-02", "PROF-03"
        );
        when(defenseRepository.existsByStudentMatricule("ETU-2026-001")).thenReturn(false);
        when(registrarSoapClient.verifyEligibility("ETU-2026-001")).thenReturn(ClearanceResult.provisional());
        when(defenseRepository.save(any(DefenseRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DefenseResponseDto result = defenseService.create(dto);

        assertThat(result.status()).isEqualTo(DefenseStatus.PROVISIONAL_PENDING_CLEARANCE);
        assertThat(result.studentName()).isNull();
        assertThat(result.clearanceToken()).isNull();
    }

    @Test
    void create_shouldSetEligibleWithTokenAndOfficialNameWhenSoapSucceeds() {
        DefenseRequestDto dto = new DefenseRequestDto(
                "ETU-2026-001", "Nom Saisi", "Plateforme SOA JuryOpt",
                "PROF-01", "PROF-02", "PROF-03"
        );
        when(defenseRepository.existsByStudentMatricule("ETU-2026-001")).thenReturn(false);
        when(registrarSoapClient.verifyEligibility("ETU-2026-001"))
                .thenReturn(ClearanceResult.verified("Amine Ben Salem", "CLR-12345678"));
        when(defenseRepository.save(any(DefenseRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DefenseResponseDto result = defenseService.create(dto);

        assertThat(result.status()).isEqualTo(DefenseStatus.ELIGIBLE);
        assertThat(result.studentName()).isEqualTo("Amine Ben Salem");
        assertThat(result.clearanceToken()).isEqualTo("CLR-12345678");
    }

    @Test
    void create_shouldNotPersistWhenSoapReturnsBusinessError() {
        DefenseRequestDto dto = new DefenseRequestDto(
                "ETU-2026-005", null, "Plateforme SOA JuryOpt",
                "PROF-01", "PROF-02", "PROF-03"
        );
        when(defenseRepository.existsByStudentMatricule("ETU-2026-005")).thenReturn(false);
        when(registrarSoapClient.verifyEligibility("ETU-2026-005"))
                .thenThrow(new BusinessRuleException("Crédits insuffisants (150/180)"));

        assertThatThrownBy(() -> defenseService.create(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("150/180");

        verify(defenseRepository, never()).save(any());
    }

    @Test
    void create_shouldNotPersistWhenSoapReturnsStudentNotFound() {
        DefenseRequestDto dto = new DefenseRequestDto(
                "ETU-2026-999", null, "Plateforme SOA JuryOpt",
                "PROF-01", "PROF-02", "PROF-03"
        );
        when(defenseRepository.existsByStudentMatricule("ETU-2026-999")).thenReturn(false);
        when(registrarSoapClient.verifyEligibility("ETU-2026-999"))
                .thenThrow(new ResourceNotFoundException("Aucun dossier académique trouvé"));

        assertThatThrownBy(() -> defenseService.create(dto))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(defenseRepository, never()).save(any());
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
        verifyNoInteractions(registrarSoapClient);
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
        verifyNoInteractions(registrarSoapClient);
    }

    @Test
    void recheckClearance_shouldPromoteProvisionalToEligible() {
        DefenseRequest existing = new DefenseRequest(
                "ETU-2026-001", null, "Plateforme SOA JuryOpt",
                "PROF-01", "PROF-02", "PROF-03", DefenseStatus.PROVISIONAL_PENDING_CLEARANCE
        );
        when(defenseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(registrarSoapClient.verifyEligibility("ETU-2026-001"))
                .thenReturn(ClearanceResult.verified("Amine Ben Salem", "CLR-ABCDEF12"));
        when(defenseRepository.save(any(DefenseRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DefenseResponseDto result = defenseService.recheckClearance(1L);

        assertThat(result.status()).isEqualTo(DefenseStatus.ELIGIBLE);
        assertThat(result.studentName()).isEqualTo("Amine Ben Salem");
        assertThat(result.clearanceToken()).isEqualTo("CLR-ABCDEF12");
    }

    @Test
    void recheckClearance_shouldThrow503WhenSoapStillUnavailable() {
        DefenseRequest existing = new DefenseRequest(
                "ETU-2026-001", null, "Plateforme SOA JuryOpt",
                "PROF-01", "PROF-02", "PROF-03", DefenseStatus.PROVISIONAL_PENDING_CLEARANCE
        );
        when(defenseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(registrarSoapClient.verifyEligibility("ETU-2026-001")).thenReturn(ClearanceResult.provisional());

        assertThatThrownBy(() -> defenseService.recheckClearance(1L))
                .isInstanceOf(ExternalServiceUnavailableException.class);

        verify(defenseRepository, never()).save(any());
    }

    @Test
    void recheckClearance_shouldRejectNonProvisionalDefense() {
        DefenseRequest existing = new DefenseRequest(
                "ETU-2026-001", "Amine Ben Salem", "Plateforme SOA JuryOpt",
                "PROF-01", "PROF-02", "PROF-03", DefenseStatus.ELIGIBLE
        );
        when(defenseRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> defenseService.recheckClearance(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PROVISIONAL_PENDING_CLEARANCE");

        verifyNoInteractions(registrarSoapClient);
    }

    @Test
    void update_whenMatriculeChanges_shouldResetToProvisionalAndClearTokenAndName() {
        DefenseRequest existing = new DefenseRequest(
                "ETU-2026-001", "Amine Ben Salem", "Ancien titre",
                "PROF-01", "PROF-02", "PROF-03", DefenseStatus.ELIGIBLE
        );
        existing.setClearanceToken("CLR-12345678");
        DefenseRequestDto dto = new DefenseRequestDto(
                "ETU-2026-002", "Sarra Trabelsi", "Nouveau titre PFE",
                "PROF-01", "PROF-02", "PROF-04"
        );
        when(defenseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(defenseRepository.existsByStudentMatriculeAndIdNot("ETU-2026-002", 1L)).thenReturn(false);
        when(defenseRepository.save(any(DefenseRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DefenseResponseDto result = defenseService.update(1L, dto);

        assertThat(result.studentMatricule()).isEqualTo("ETU-2026-002");
        assertThat(result.status()).isEqualTo(DefenseStatus.PROVISIONAL_PENDING_CLEARANCE);
        assertThat(result.clearanceToken()).isNull();
        assertThat(result.studentName()).isNull();
        assertThat(result.projectTitle()).isEqualTo("Nouveau titre PFE");
        assertThat(result.rapporteurCode()).isEqualTo("PROF-04");
    }

    @Test
    void update_whenMatriculeUnchangedOnEligibleDefense_shouldKeepStatusTokenAndOfficialName() {
        DefenseRequest existing = new DefenseRequest(
                "ETU-2026-001", "Amine Ben Salem", "Ancien titre",
                "PROF-01", "PROF-02", "PROF-03", DefenseStatus.ELIGIBLE
        );
        existing.setClearanceToken("CLR-12345678");
        DefenseRequestDto dto = new DefenseRequestDto(
                "ETU-2026-001", "Autre Nom Saisi", "Nouveau titre PFE",
                "PROF-01", "PROF-02", "PROF-04"
        );
        when(defenseRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(defenseRepository.existsByStudentMatriculeAndIdNot("ETU-2026-001", 1L)).thenReturn(false);
        when(defenseRepository.save(any(DefenseRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DefenseResponseDto result = defenseService.update(1L, dto);

        assertThat(result.studentMatricule()).isEqualTo("ETU-2026-001");
        assertThat(result.status()).isEqualTo(DefenseStatus.ELIGIBLE);
        assertThat(result.clearanceToken()).isEqualTo("CLR-12345678");
        assertThat(result.studentName()).isEqualTo("Amine Ben Salem");
        assertThat(result.projectTitle()).isEqualTo("Nouveau titre PFE");
        assertThat(result.rapporteurCode()).isEqualTo("PROF-04");
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