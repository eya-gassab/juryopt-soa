package com.juryopt.orchestrator.controller;

import com.juryopt.orchestrator.dto.OptimizationRunResponseDto;
import com.juryopt.orchestrator.dto.ScheduleEvaluationRequestDto;
import com.juryopt.orchestrator.dto.ScheduleEvaluationResponseDto;
import com.juryopt.orchestrator.dto.SchedulePublicationResponseDto;
import com.juryopt.orchestrator.service.ScheduleWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
@Tag(name = "Schedules", description = "Optimisation gRPC, évaluation de conflits et publication officielle SOAP")
public class ScheduleController {

    private final ScheduleWorkflowService scheduleWorkflowService;

    public ScheduleController(ScheduleWorkflowService scheduleWorkflowService) {
        this.scheduleWorkflowService = scheduleWorkflowService;
    }

    @PostMapping("/optimization-runs")
    @Operation(summary = "Lancer une optimisation de planning via gRPC (avec repli séquentiel)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Planning généré (DRAFT ou UNOPTIMIZED_FALLBACK)"),
            @ApiResponse(responseCode = "422", description = "Aucune demande éligible ou créneaux insuffisants")
    })
    public ResponseEntity<OptimizationRunResponseDto> createOptimizationRun() {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduleWorkflowService.runOptimization());
    }

    @PostMapping("/evaluations")
    @Operation(summary = "Évaluer les conflits d'un planning proposé via gRPC unaire")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résultat d'évaluation retourné"),
            @ApiResponse(responseCode = "400", description = "Requête invalide (liste vide ou champs manquants)"),
            @ApiResponse(responseCode = "422", description = "Rejet métier du moteur gRPC"),
            @ApiResponse(responseCode = "503", description = "Service d'optimisation gRPC indisponible")
    })
    public ResponseEntity<ScheduleEvaluationResponseDto> evaluate(
            @Valid @RequestBody ScheduleEvaluationRequestDto request) {
        return ResponseEntity.ok(scheduleWorkflowService.evaluateSchedule(request));
    }

    @PostMapping("/{id}/publications")
    @Operation(summary = "Publier officiellement le dernier planning DRAFT sans conflit via SOAP")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Planning publié avec les codes officiels REG-..."),
            @ApiResponse(responseCode = "404", description = "Planning ou étudiant introuvable"),
            @ApiResponse(responseCode = "409", description = "Incohérence de doublon d'enregistrement SOAP"),
            @ApiResponse(responseCode = "422", description = "Planning non publiable (UNOPTIMIZED_FALLBACK, conflits ou non dernier DRAFT)"),
            @ApiResponse(responseCode = "503", description = "Service de scolarité SOAP indisponible (les succès précédents restent enregistrés)")
    })
    public ResponseEntity<SchedulePublicationResponseDto> publish(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleWorkflowService.publishSchedule(id));
    }
}