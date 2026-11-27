package com.juryopt.orchestrator.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.juryopt.orchestrator.domain.DefenseStatus;
import com.juryopt.orchestrator.dto.DefenseResponseDto;
import com.juryopt.orchestrator.exception.BusinessRuleException;
import com.juryopt.orchestrator.exception.DuplicateResourceException;
import com.juryopt.orchestrator.exception.ExternalServiceUnavailableException;
import com.juryopt.orchestrator.exception.GlobalExceptionHandler;
import com.juryopt.orchestrator.exception.ResourceNotFoundException;
import com.juryopt.orchestrator.service.DefenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DefenseController.class)
@Import(GlobalExceptionHandler.class)
class DefenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DefenseService defenseService;

    private static final String VALID_PAYLOAD = """
            {
              "studentMatricule": "ETU-2026-001",
              "studentName": "Amine Ben Salem",
              "projectTitle": "Plateforme SOA de planification des soutenances",
              "supervisorCode": "PROF-01",
              "presidentCode": "PROF-02",
              "rapporteurCode": "PROF-03"
            }
            """;

    @Test
    void postDefense_shouldReturn201WithLocationHeader() throws Exception {
        DefenseResponseDto response = new DefenseResponseDto(
                1L, "ETU-2026-001", "Amine Ben Salem",
                "Plateforme SOA de planification des soutenances",
                "PROF-01", "PROF-02", "PROF-03",
                DefenseStatus.PROVISIONAL_PENDING_CLEARANCE,
                null, null, null, null
        );
        when(defenseService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/defenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/defenses/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PROVISIONAL_PENDING_CLEARANCE"));
    }

    @Test
    void postDefense_withInvalidMatricule_shouldReturn400ProblemDetail() throws Exception {
        String invalidPayload = """
                {
                  "studentMatricule": "BAD-FORMAT",
                  "projectTitle": "Plateforme SOA",
                  "supervisorCode": "PROF-01",
                  "presidentCode": "PROF-02",
                  "rapporteurCode": "PROF-03"
                }
                """;

        mockMvc.perform(post("/api/v1/defenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.studentMatricule").exists());
    }

    @Test
    void getDefenseById_whenMissing_shouldReturn404ProblemDetail() throws Exception {
        when(defenseService.findById(999L))
                .thenThrow(new ResourceNotFoundException("Demande introuvable avec l'id 999"));

        mockMvc.perform(get("/api/v1/defenses/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void postDefense_withBusinessRuleViolation_shouldReturn422ProblemDetail() throws Exception {
        when(defenseService.create(any()))
                .thenThrow(new BusinessRuleException("Les trois membres du jury doivent être distincts."));

        mockMvc.perform(post("/api/v1/defenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Business Rule Violation"))
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void postDefense_withDuplicateMatricule_shouldReturn409ProblemDetail() throws Exception {
        when(defenseService.create(any()))
                .thenThrow(new DuplicateResourceException("Une demande existe déjà pour ETU-2026-001"));

        mockMvc.perform(post("/api/v1/defenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYLOAD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource Conflict"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void postDefenseClearance_shouldReturn200WhenEligible() throws Exception {
        DefenseResponseDto response = new DefenseResponseDto(
                1L, "ETU-2026-001", "Amine Ben Salem",
                "Plateforme SOA de planification des soutenances",
                "PROF-01", "PROF-02", "PROF-03",
                DefenseStatus.ELIGIBLE,
                "CLR-12345678", null, null, null
        );
        when(defenseService.recheckClearance(1L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/defenses/1/clearances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.clearanceToken").value("CLR-12345678"));
    }

    @Test
    void postDefenseClearance_whenSoapDown_shouldReturn503ProblemDetail() throws Exception {
        when(defenseService.recheckClearance(1L))
                .thenThrow(new ExternalServiceUnavailableException("Service de scolarité indisponible"));

        mockMvc.perform(post("/api/v1/defenses/1/clearances"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void deleteDefense_shouldReturn204() throws Exception {
        doNothing().when(defenseService).delete(1L);

        mockMvc.perform(delete("/api/v1/defenses/1"))
                .andExpect(status().isNoContent());
    }
}