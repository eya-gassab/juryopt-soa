package com.juryopt.orchestrator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ScheduleEvaluationRequestDto(
        @NotEmpty(message = "La liste des affectations à évaluer ne peut pas être vide")
        List<@Valid ProposedAssignmentDto> assignments
) {
}