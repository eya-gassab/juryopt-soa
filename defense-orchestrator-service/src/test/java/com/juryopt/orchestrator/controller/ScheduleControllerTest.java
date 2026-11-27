package com.juryopt.orchestrator.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.juryopt.orchestrator.domain.DefenseStatus;
import com.juryopt.orchestrator.domain.ScheduleStatus;
import com.juryopt.orchestrator.dto.OptimizationRunResponseDto;
import com.juryopt.orchestrator.dto.OptimizationStepDto;
import com.juryopt.orchestrator.dto.ProposedAssignmentDto;
import com.juryopt.orchestrator.dto.PublishedDefenseDto;
import com.juryopt.orchestrator.dto.ScheduleEvaluationResponseDto;
import com.juryopt.orchestrator.dto.SchedulePublicationResponseDto;
import com.juryopt.orchestrator.exception.BusinessRuleException;
import com.juryopt.orchestrator.exception.ExternalServiceUnavailableException;
import com.juryopt.orchestrator.exception.GlobalExceptionHandler;
import com.juryopt.orchestrator.service.ScheduleWorkflowService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScheduleController.class)
@Import(GlobalExceptionHandler.class)
class ScheduleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScheduleWorkflowService scheduleWorkflowService;

    @Test
    void postOptimizationRuns_shouldReturn201WithSteps() throws Exception {
        OptimizationRunResponseDto response = new OptimizationRunResponseDto(
                1L, LocalDateTime.of(2026, 11, 26, 21, 0),
                ScheduleStatus.DRAFT, false, 1, 0, 0.0,
                List.of(new OptimizationStepDto("COMPLETED", 3, 1, 0, 0.0)),
                List.of(new ProposedAssignmentDto("1", "2026-06-15_09:00", "SALLE-A1", "PROF-01", "PROF-02", "PROF-03"))
        );
        when(scheduleWorkflowService.runOptimization()).thenReturn(response);

        mockMvc.perform(post("/api/v1/schedules/optimization-runs"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.steps[0].stage").value("COMPLETED"));
    }

    @Test
    void postEvaluations_shouldReturn200WhenValid() throws Exception {
        when(scheduleWorkflowService.evaluateSchedule(any()))
                .thenReturn(new ScheduleEvaluationResponseDto(true, 0, List.of()));

        String payload = """
                {
                  "assignments": [
                    {
                      "requestId": "1",
                      "slotCode": "2026-06-15_09:00",
                      "roomCode": "SALLE-A1",
                      "supervisorCode": "PROF-01",
                      "presidentCode": "PROF-02",
                      "rapporteurCode": "PROF-03"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/schedules/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.conflictCount").value(0));
    }

    @Test
    void postEvaluations_whenOptimizerDown_shouldReturn503ProblemDetail() throws Exception {
        when(scheduleWorkflowService.evaluateSchedule(any()))
                .thenThrow(new ExternalServiceUnavailableException("Service gRPC indisponible"));

        String payload = """
                {
                  "assignments": [
                    {
                      "requestId": "1",
                      "slotCode": "2026-06-15_09:00",
                      "roomCode": "SALLE-A1",
                      "supervisorCode": "PROF-01",
                      "presidentCode": "PROF-02",
                      "rapporteurCode": "PROF-03"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/schedules/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void postPublications_whenFallbackRefused_shouldReturn422ProblemDetail() throws Exception {
        when(scheduleWorkflowService.publishSchedule(1L))
                .thenThrow(new BusinessRuleException("Un planning UNOPTIMIZED_FALLBACK ne peut pas être publié."));

        mockMvc.perform(post("/api/v1/schedules/1/publications"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void postPublications_shouldReturn200WhenPublished() throws Exception {
        SchedulePublicationResponseDto response = new SchedulePublicationResponseDto(
                1L, ScheduleStatus.PUBLISHED, 1,
                List.of(new PublishedDefenseDto(1L, "ETU-2026-001", "REG-2026-0001", DefenseStatus.PUBLISHED, false))
        );
        when(scheduleWorkflowService.publishSchedule(1L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/schedules/1/publications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.defenses[0].registryCode").value("REG-2026-0001"));
    }
}
