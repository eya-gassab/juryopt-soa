package com.juryopt.orchestrator.service;

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
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefenseService {

    private final DefenseRequestRepository defenseRepository;
    private final RegistrarSoapClient registrarSoapClient;

    public DefenseService(DefenseRequestRepository defenseRepository,
                          RegistrarSoapClient registrarSoapClient) {
        this.defenseRepository = defenseRepository;
        this.registrarSoapClient = registrarSoapClient;
    }

    public DefenseResponseDto create(DefenseRequestDto dto) {
        validateDistinctJury(dto);

        if (defenseRepository.existsByStudentMatricule(dto.studentMatricule())) {
            throw new DuplicateResourceException(
                    "Une demande existe déjà pour le matricule " + dto.studentMatricule()
            );
        }

        ClearanceResult clearance = registrarSoapClient.verifyEligibility(dto.studentMatricule());

        DefenseStatus status = clearance.verified()
                ? DefenseStatus.ELIGIBLE
                : DefenseStatus.PROVISIONAL_PENDING_CLEARANCE;
        String resolvedName = clearance.verified()
                ? clearance.fullName()
                : normalizeOptionalText(dto.studentName());

        DefenseRequest entity = new DefenseRequest(
                dto.studentMatricule(),
                resolvedName,
                dto.projectTitle().trim(),
                dto.supervisorCode(),
                dto.presidentCode(),
                dto.rapporteurCode(),
                status
        );
        if (clearance.verified()) {
            entity.setClearanceToken(clearance.clearanceToken());
        }

        try {
            return DefenseResponseDto.from(defenseRepository.save(entity));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "Une demande existe déjà pour le matricule " + dto.studentMatricule()
            );
        }
    }

    public DefenseResponseDto recheckClearance(Long id) {
        DefenseRequest existing = getOrThrow(id);

        if (existing.getStatus() != DefenseStatus.PROVISIONAL_PENDING_CLEARANCE) {
            throw new BusinessRuleException(
                    "La revérification n'est autorisée que pour une demande PROVISIONAL_PENDING_CLEARANCE (statut actuel : "
                            + existing.getStatus() + ")."
            );
        }

        ClearanceResult clearance = registrarSoapClient.verifyEligibility(existing.getStudentMatricule());
        if (!clearance.verified()) {
            throw new ExternalServiceUnavailableException(
                    "Le service de scolarité est temporairement indisponible. Impossible de revérifier la demande " + id + "."
            );
        }

        existing.setStatus(DefenseStatus.ELIGIBLE);
        existing.setStudentName(clearance.fullName());
        existing.setClearanceToken(clearance.clearanceToken());

        return DefenseResponseDto.from(defenseRepository.save(existing));
    }

    @Transactional(readOnly = true)
    public List<DefenseResponseDto> findAll() {
        return defenseRepository.findAll().stream()
                .map(DefenseResponseDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DefenseResponseDto findById(Long id) {
        return DefenseResponseDto.from(getOrThrow(id));
    }

    @Transactional
    public DefenseResponseDto update(Long id, DefenseRequestDto dto) {
        DefenseRequest existing = getOrThrow(id);
        ensureModifiable(existing);
        validateDistinctJury(dto);

        if (defenseRepository.existsByStudentMatriculeAndIdNot(dto.studentMatricule(), id)) {
            throw new DuplicateResourceException(
                    "Une autre demande existe déjà pour le matricule " + dto.studentMatricule()
            );
        }

        boolean matriculeChanged = !existing.getStudentMatricule().equals(dto.studentMatricule());
        if (matriculeChanged) {
            existing.setStudentMatricule(dto.studentMatricule());
            existing.setStatus(DefenseStatus.PROVISIONAL_PENDING_CLEARANCE);
            existing.setClearanceToken(null);
            existing.setStudentName(null);
        } else if (existing.getStatus() != DefenseStatus.ELIGIBLE) {
            existing.setStudentName(normalizeOptionalText(dto.studentName()));
        }

        existing.setProjectTitle(dto.projectTitle().trim());
        existing.setSupervisorCode(dto.supervisorCode());
        existing.setPresidentCode(dto.presidentCode());
        existing.setRapporteurCode(dto.rapporteurCode());

        return DefenseResponseDto.from(defenseRepository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        DefenseRequest existing = getOrThrow(id);
        ensureModifiable(existing);
        defenseRepository.delete(existing);
    }

    private DefenseRequest getOrThrow(Long id) {
        return defenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Demande de soutenance introuvable avec l'id " + id
                ));
    }

    private void ensureModifiable(DefenseRequest request) {
        if (request.getStatus() == DefenseStatus.SCHEDULED
                || request.getStatus() == DefenseStatus.PUBLISHED) {
            throw new BusinessRuleException(
                    "Impossible de modifier ou supprimer une soutenance au statut " + request.getStatus()
            );
        }
    }

    private void validateDistinctJury(DefenseRequestDto dto) {
        if (dto.supervisorCode().equals(dto.presidentCode())
                || dto.supervisorCode().equals(dto.rapporteurCode())
                || dto.presidentCode().equals(dto.rapporteurCode())) {
            throw new BusinessRuleException(
                    "Les trois membres du jury (encadrant, président, rapporteur) doivent être distincts."
            );
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}