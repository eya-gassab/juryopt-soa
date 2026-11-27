package com.juryopt.orchestrator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ProposedAssignmentDto(
        @Schema(example = "1")
        @NotBlank(message = "L'identifiant de demande est obligatoire")
        String requestId,

        @Schema(example = "2026-06-15_09:00")
        @NotBlank(message = "Le code créneau est obligatoire")
        String slotCode,

        @Schema(example = "SALLE-A1")
        @NotBlank(message = "Le code salle est obligatoire")
        String roomCode,

        @Schema(example = "PROF-01")
        @NotBlank(message = "Le code encadrant est obligatoire")
        String supervisorCode,

        @Schema(example = "PROF-02")
        @NotBlank(message = "Le code président est obligatoire")
        String presidentCode,

        @Schema(example = "PROF-03")
        @NotBlank(message = "Le code rapporteur est obligatoire")
        String rapporteurCode
) {
}