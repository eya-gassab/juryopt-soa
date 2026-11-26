package com.juryopt.orchestrator.dto;

import com.juryopt.orchestrator.domain.DefenseRequest;
import com.juryopt.orchestrator.domain.DefenseStatus;

public record DefenseResponseDto(
        Long id,
        String studentMatricule,
        String studentName,
        String projectTitle,
        String supervisorCode,
        String presidentCode,
        String rapporteurCode,
        DefenseStatus status,
        String clearanceToken,
        String registryCode,
        String assignedSlotCode,
        String assignedRoomCode
) {
    public static DefenseResponseDto from(DefenseRequest entity) {
        return new DefenseResponseDto(
                entity.getId(),
                entity.getStudentMatricule(),
                entity.getStudentName(),
                entity.getProjectTitle(),
                entity.getSupervisorCode(),
                entity.getPresidentCode(),
                entity.getRapporteurCode(),
                entity.getStatus(),
                entity.getClearanceToken(),
                entity.getRegistryCode(),
                entity.getAssignedSlotCode(),
                entity.getAssignedRoomCode()
        );
    }
}