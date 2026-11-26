package com.juryopt.orchestrator.controller;

import com.juryopt.orchestrator.dto.SlotRequestDto;
import com.juryopt.orchestrator.dto.SlotResponseDto;
import com.juryopt.orchestrator.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/slots")
@Tag(name = "Slots", description = "Gestion des créneaux et salles disponibles")
public class SlotController {

    private final SlotService slotService;

    public SlotController(SlotService slotService) {
        this.slotService = slotService;
    }

    @PostMapping
    @Operation(summary = "Créer un créneau/salle disponible")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Créneau créé (en-tête Location renseigné)"),
            @ApiResponse(responseCode = "400", description = "Données d'entrée invalides"),
            @ApiResponse(responseCode = "409", description = "Le couple (créneau, salle) existe déjà")
    })
    public ResponseEntity<SlotResponseDto> create(@Valid @RequestBody SlotRequestDto request) {
        SlotResponseDto created = slotService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "Lister tous les créneaux")
    @ApiResponse(responseCode = "200", description = "Liste des créneaux retournée")
    public ResponseEntity<List<SlotResponseDto>> findAll() {
        return ResponseEntity.ok(slotService.findAll());
    }
}