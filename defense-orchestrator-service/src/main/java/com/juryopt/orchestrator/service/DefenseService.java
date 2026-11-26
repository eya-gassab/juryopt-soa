package com.juryopt.orchestrator.service;

import com.juryopt.orchestrator.domain.DefenseRequest;
import com.juryopt.orchestrator.domain.DefenseStatus;
import com.juryopt.orchestrator.dto.DefenseRequestDto;
import com.juryopt.orchestrator.dto.DefenseResponseDto;
import com.juryopt.orchestrator.exception.BusinessRuleException;
import com.juryopt.orchestrator.exception.DuplicateResourceException;
import com.juryopt.orchestrator.exception.ResourceNotFoundException;
import com.juryopt.orchestrator.repository.DefenseRequestRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefenseService {

    private final DefenseRequestRepository defenseRepository;

    public DefenseService(DefenseRequestRepository defenseRepository) {
        this.defenseRepository = defenseRepository;
    }

    @Transactional
    public DefenseResponseDto create(DefenseRequestDto dto) {
        validateDistinctJury(dto);

        if (defenseRepository.existsByStudentMatricule(dto.studentMatricule())) {
            throw new DuplicateResourceException(
                    "Une demande existe déjà pour le matricule " + dto.studentMatricule()
            );
        }

        DefenseRequest entity = new DefenseRequest(
                dto.studentMatricule(),
                normalizeOptionalText(dto.studentName()),
                dto.projectTitle().trim(),
                dto.supervisorCode(),
                dto.presidentCode(),
                dto.rapporteurCode(),
                DefenseStatus.PROVISIONAL_PENDING_CLEARANCE
        );

        return DefenseResponseDto.from(defenseRepository.save(entity));
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

        existing.setStudentMatricule(dto.studentMatricule());
        existing.setStudentName(normalizeOptionalText(dto.studentName()));
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