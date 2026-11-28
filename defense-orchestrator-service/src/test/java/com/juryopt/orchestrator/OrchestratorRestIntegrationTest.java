package com.juryopt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juryopt.orchestrator.client.OptimizerGrpcClient;
import com.juryopt.orchestrator.client.OptimizerGrpcClient.OptimizationResult;
import com.juryopt.orchestrator.client.RegistrarSoapClient;
import com.juryopt.orchestrator.client.RegistrarSoapClient.ClearanceResult;
import com.juryopt.orchestrator.client.RegistrarSoapClient.RegistrationResult;
import com.juryopt.orchestrator.dto.OptimizationStepDto;
import com.juryopt.orchestrator.dto.ProposedAssignmentDto;
import com.juryopt.orchestrator.exception.BusinessRuleException;
import com.juryopt.orchestrator.repository.DefenseRequestRepository;
import com.juryopt.orchestrator.repository.DefenseScheduleRepository;
import com.juryopt.orchestrator.repository.DefenseSlotRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrchestratorRestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DefenseRequestRepository defenseRepository;

    @Autowired
    private DefenseSlotRepository slotRepository;

    @Autowired
    private DefenseScheduleRepository scheduleRepository;

    @MockBean
    private RegistrarSoapClient registrarSoapClient;

    @MockBean
    private OptimizerGrpcClient optimizerGrpcClient;

    @BeforeEach
    void setUp() {
        defenseRepository.deleteAll();
        slotRepository.deleteAll();
        scheduleRepository.deleteAll();
    }

    @Test
    void fullWorkflow_createUpdateMatriculeRecheckOptimizePublishAndReserveSlot() throws Exception {
        when(registrarSoapClient.verifyEligibility("ETU-2026-001"))
                .thenReturn(ClearanceResult.verified("Amine Ben Salem", "CLR-11111111"));

        String createPayload = """
                {
                  "studentMatricule": "ETU-2026-001",
                  "projectTitle": "Plateforme SOA de planification des soutenances",
                  "supervisorCode": "PROF-01",
                  "presidentCode": "PROF-02",
                  "rapporteurCode": "PROF-03"
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/defenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.studentName").value("Amine Ben Salem"))
                .andExpect(jsonPath("$.clearanceToken").value("CLR-11111111"))
                .andReturn();

        long defenseId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // Changement de matricule -> repasse en PROVISIONAL_PENDING_CLEARANCE sans token ni nom
        String updateMatriculePayload = """
                {
                  "studentMatricule": "ETU-2026-002",
                  "studentName": "Nom Manuel",
                  "projectTitle": "Plateforme SOA mise a jour",
                  "supervisorCode": "PROF-01",
                  "presidentCode": "PROF-02",
                  "rapporteurCode": "PROF-03"
                }
                """;

        mockMvc.perform(put("/api/v1/defenses/{id}", defenseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateMatriculePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentMatricule").value("ETU-2026-002"))
                .andExpect(jsonPath("$.status").value("PROVISIONAL_PENDING_CLEARANCE"))
                .andExpect(jsonPath("$.clearanceToken").doesNotExist())
                .andExpect(jsonPath("$.studentName").doesNotExist());

        // Revérification SOAP -> redevient ELIGIBLE
        when(registrarSoapClient.verifyEligibility("ETU-2026-002"))
                .thenReturn(ClearanceResult.verified("Sarra Trabelsi", "CLR-22222222"));

        mockMvc.perform(post("/api/v1/defenses/{id}/clearances", defenseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.studentName").value("Sarra Trabelsi"))
                .andExpect(jsonPath("$.clearanceToken").value("CLR-22222222"));

        // Création d'un créneau disponible
        mockMvc.perform(post("/api/v1/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotCode": "2026-06-15_09:00",
                                  "roomCode": "SALLE-A1",
                                  "available": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(true));

        // Optimisation via client gRPC simulé
        OptimizationResult optResult = new OptimizationResult(
                false,
                1,
                0,
                0.0,
                List.of(new OptimizationStepDto("COMPLETED", 3, 1, 0, 0.0)),
                List.of(new ProposedAssignmentDto(
                        String.valueOf(defenseId),
                        "2026-06-15_09:00",
                        "SALLE-A1",
                        "PROF-01",
                        "PROF-02",
                        "PROF-03"
                ))
        );
        when(optimizerGrpcClient.optimizeSchedule(any(), any())).thenReturn(optResult);

        MvcResult optMvcResult = mockMvc.perform(post("/api/v1/schedules/optimization-runs"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalAssigned").value(1))
                .andExpect(jsonPath("$.conflictCount").value(0))
                .andReturn();

        JsonNode optJson = objectMapper.readTree(optMvcResult.getResponse().getContentAsString());
        long scheduleId = optJson.get("scheduleId").asLong();

        // Publication officielle -> statut PUBLISHED + créneau réservé (available = false)
        when(registrarSoapClient.registerOfficialDefense(
                eq("ETU-2026-002"), eq("CLR-22222222"), eq("2026-06-15_09:00"), eq("SALLE-A1"), any()))
                .thenReturn(new RegistrationResult("REG-2026-0001", "2026-11-28T09:00:00"));

        mockMvc.perform(post("/api/v1/schedules/{id}/publications", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.defenses[0].registryCode").value("REG-2026-0001"))
                .andExpect(jsonPath("$.defenses[0].status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slotCode").value("2026-06-15_09:00"))
                .andExpect(jsonPath("$[0].available").value(false));

        mockMvc.perform(get("/api/v1/defenses/{id}", defenseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.registryCode").value("REG-2026-0001"));
    }

    @Test
    void createDefense_whenSoapProvisionalFallback_shouldPersistProvisionalRequest() throws Exception {
        when(registrarSoapClient.verifyEligibility("ETU-2026-003"))
                .thenReturn(ClearanceResult.provisional());

        mockMvc.perform(post("/api/v1/defenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentMatricule": "ETU-2026-003",
                                  "studentName": "Youssef Khelifi",
                                  "projectTitle": "Optimisation multi-criteres des jurys",
                                  "supervisorCode": "PROF-01",
                                  "presidentCode": "PROF-02",
                                  "rapporteurCode": "PROF-03"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROVISIONAL_PENDING_CLEARANCE"))
                .andExpect(jsonPath("$.studentName").value("Youssef Khelifi"));

        assertThat(defenseRepository.count()).isEqualTo(1);
    }

    @Test
    void errorHandling_shouldReturn400_404_409_422AndNotPersistRejectedRequests() throws Exception {
        // 400 validation
        mockMvc.perform(post("/api/v1/defenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentMatricule": "INVALID",
                                  "projectTitle": "Sujet valide",
                                  "supervisorCode": "PROF-01",
                                  "presidentCode": "PROF-02",
                                  "rapporteurCode": "PROF-03"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 404 introuvable
        mockMvc.perform(get("/api/v1/defenses/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // 422 erreur métier SOAP -> rien en base
        when(registrarSoapClient.verifyEligibility("ETU-2026-005"))
                .thenThrow(new BusinessRuleException("Crédits insuffisants (150/180)"));

        mockMvc.perform(post("/api/v1/defenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentMatricule": "ETU-2026-005",
                                  "projectTitle": "Sujet non eligible",
                                  "supervisorCode": "PROF-01",
                                  "presidentCode": "PROF-02",
                                  "rapporteurCode": "PROF-03"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));

        assertThat(defenseRepository.count()).isZero();

        // 409 doublon de matricule
        when(registrarSoapClient.verifyEligibility("ETU-2026-001"))
                .thenReturn(ClearanceResult.verified("Amine Ben Salem", "CLR-11111111"));

        String validPayload = """
                {
                  "studentMatricule": "ETU-2026-001",
                  "projectTitle": "Plateforme SOA JuryOpt",
                  "supervisorCode": "PROF-01",
                  "presidentCode": "PROF-02",
                  "rapporteurCode": "PROF-03"
                }
                """;

        mockMvc.perform(post("/api/v1/defenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/defenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(defenseRepository.count()).isEqualTo(1);
    }
}