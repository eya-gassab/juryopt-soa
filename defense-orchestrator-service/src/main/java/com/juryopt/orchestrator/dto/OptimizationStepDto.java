package com.juryopt.orchestrator.dto;

public record OptimizationStepDto(
        String stage,
        int passNumber,
        int assignedCount,
        int conflictCount,
        double balanceScore
) {
}