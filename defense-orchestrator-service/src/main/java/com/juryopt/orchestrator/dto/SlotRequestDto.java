package com.juryopt.orchestrator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SlotRequestDto(
        @Schema(example = "2026-06-15_09:00")
        @NotBlank(message = "Le code créneau est obligatoire")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}_\\d{2}:\\d{2}$",
                message = "Format attendu : AAAA-MM-JJ_HH:MM (ex. 2026-06-15_09:00)")
        String slotCode,

        @Schema(example = "SALLE-A1")
        @NotBlank(message = "Le code salle est obligatoire")
        @Pattern(regexp = "^[A-Z]+-[A-Z0-9]+$",
                message = "Format attendu : SALLE-A1")
        String roomCode,

        @Schema(example = "true", defaultValue = "true")
        Boolean available
) {
}