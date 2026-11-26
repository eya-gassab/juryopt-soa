package com.juryopt.orchestrator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DefenseRequestDto(
        @Schema(example = "ETU-2026-001")
        @NotBlank(message = "Le matricule étudiant est obligatoire")
        @Size(max = 20, message = "Le matricule ne doit pas dépasser 20 caractères")
        @Pattern(regexp = "^ETU-\\d{4}-\\d{3}$", message = "Format attendu : ETU-AAAA-NNN (ex. ETU-2026-001)")
        String studentMatricule,

        @Schema(example = "Amine Ben Salem", nullable = true)
        @Size(max = 120, message = "Le nom de l'étudiant ne doit pas dépasser 120 caractères")
        String studentName,

        @Schema(example = "Plateforme SOA de planification des soutenances")
        @NotBlank(message = "Le titre du projet est obligatoire")
        @Size(min = 5, max = 200, message = "Le titre doit contenir entre 5 et 200 caractères")
        String projectTitle,

        @Schema(example = "PROF-01")
        @NotBlank(message = "Le code encadrant est obligatoire")
        @Pattern(regexp = "^PROF-\\d{2,4}$", message = "Format attendu : PROF-NN (ex. PROF-01)")
        String supervisorCode,

        @Schema(example = "PROF-02")
        @NotBlank(message = "Le code président est obligatoire")
        @Pattern(regexp = "^PROF-\\d{2,4}$", message = "Format attendu : PROF-NN (ex. PROF-02)")
        String presidentCode,

        @Schema(example = "PROF-03")
        @NotBlank(message = "Le code rapporteur est obligatoire")
        @Pattern(regexp = "^PROF-\\d{2,4}$", message = "Format attendu : PROF-NN (ex. PROF-03)")
        String rapporteurCode
) {
}