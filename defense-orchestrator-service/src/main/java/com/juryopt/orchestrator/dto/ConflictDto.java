package com.juryopt.orchestrator.dto;

import java.util.List;

public record ConflictDto(
        String type,
        String slotCode,
        String resourceCode,
        List<String> requestIds
) {
}