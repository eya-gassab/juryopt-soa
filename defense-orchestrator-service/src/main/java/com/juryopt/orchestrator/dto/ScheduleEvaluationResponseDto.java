package com.juryopt.orchestrator.dto;

import java.util.List;

public record ScheduleEvaluationResponseDto(
        boolean valid,
        int conflictCount,
        List<ConflictDto> conflicts
) {
}