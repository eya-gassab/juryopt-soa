package com.juryopt.orchestrator.dto;

import com.juryopt.orchestrator.domain.ScheduleStatus;
import java.util.List;

public record SchedulePublicationResponseDto(
        Long scheduleId,
        ScheduleStatus scheduleStatus,
        int publishedCount,
        List<PublishedDefenseDto> defenses
) {
}