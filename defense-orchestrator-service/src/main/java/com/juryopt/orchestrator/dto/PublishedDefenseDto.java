package com.juryopt.orchestrator.dto;

import com.juryopt.orchestrator.domain.DefenseStatus;

public record PublishedDefenseDto(
        Long defenseId,
        String studentMatricule,
        String registryCode,
        DefenseStatus status,
        boolean alreadyRegistered
) {
}