package com.juryopt.orchestrator.dto;

import com.juryopt.orchestrator.domain.ScheduleStatus;
import java.time.LocalDateTime;
import java.util.List;

public record OptimizationRunResponseDto(
        Long scheduleId,
        LocalDateTime createdAt,
        ScheduleStatus status,
        boolean fallbackUsed,
        int totalAssigned,
        int conflictCount,
        double balanceScore,
        List<OptimizationStepDto> steps,
        List<ProposedAssignmentDto> assignments
) {
}