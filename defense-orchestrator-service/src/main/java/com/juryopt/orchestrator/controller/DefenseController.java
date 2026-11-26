package com.juryopt.orchestrator.controller;

import com.juryopt.orchestrator.dto.DefenseRequestDto;
import com.juryopt.orchestrator.dto.DefenseResponseDto;
import com.juryopt.orchestrator.service.DefenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/defenses")
@Tag(name = "Defenses", description = "Gestion CRUD des demandes de soutenance de PFE")
public class DefenseController {

    private final DefenseService defenseService;

    public DefenseController(DefenseService defenseService) {
        this.defenseService = defenseService;
    }

    @PostMapping
    @Operation(summary = "Créer une demande de soutenance")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Demande créée (en-tête Location renseigné)"),
            @ApiResponse(responseCode = "400", description = "Données d'entrée invalides"),
            @ApiResponse(responseCode = "409", description = "Une demande existe déjà pour ce matricule"),
            @ApiResponse(responseCode = "422", description = "Règle métier violée (membres du jury non distincts)")
    })
    public ResponseEntity<DefenseResponseDto> create(@Valid @RequestBody DefenseRequestDto request) {
        DefenseResponseDto created = defenseService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "Lister toutes les demandes de soutenance")
    @ApiResponse(responseCode = "200", description = "Liste des demandes retournée")
    public ResponseEntity<List<DefenseResponseDto>> findAll() {
        return ResponseEntity.ok(defenseService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Récupérer une demande de soutenance par identifiant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demande trouvée"),
            @ApiResponse(responseCode = "404", description = "Demande introuvable")
    })
    public ResponseEntity<DefenseResponseDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(defenseService.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Mettre à jour une demande de soutenance")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demande mise à jour"),
            @ApiResponse(responseCode = "400", description = "Données d'entrée invalides"),
            @ApiResponse(responseCode = "404", description = "Demande introuvable"),
            @ApiResponse(responseCode = "409", description = "Matricule déjà utilisé par une autre demande"),
            @ApiResponse(responseCode = "422", description = "Jury non distinct ou demande déjà SCHEDULED/PUBLISHED")
    })
    public ResponseEntity<DefenseResponseDto> update(
            @PathVariable Long id,
            @Valid @RequestBody DefenseRequestDto request) {
        return ResponseEntity.ok(defenseService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une demande de soutenance")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Demande supprimée"),
            @ApiResponse(responseCode = "404", description = "Demande introuvable"),
            @ApiResponse(responseCode = "422", description = "Impossible de supprimer une demande SCHEDULED/PUBLISHED")
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        defenseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
