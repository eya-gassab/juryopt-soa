package com.juryopt.orchestrator.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.juryopt.orchestrator.dto.SlotResponseDto;
import com.juryopt.orchestrator.exception.GlobalExceptionHandler;
import com.juryopt.orchestrator.service.SlotService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SlotController.class)
@Import(GlobalExceptionHandler.class)
class SlotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SlotService slotService;

    @Test
    void postSlot_shouldReturn201WithLocationHeader() throws Exception {
        when(slotService.create(any()))
                .thenReturn(new SlotResponseDto(1L, "2026-06-15_09:00", "SALLE-A1", true));

        String payload = """
                {
                  "slotCode": "2026-06-15_09:00",
                  "roomCode": "SALLE-A1",
                  "available": true
                }
                """;

        mockMvc.perform(post("/api/v1/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/slots/1")))
                .andExpect(jsonPath("$.slotCode").value("2026-06-15_09:00"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void postSlot_withInvalidSlotFormat_shouldReturn400ProblemDetail() throws Exception {
        String invalidPayload = """
                {
                  "slotCode": "15-06-2026 09h",
                  "roomCode": "SALLE-A1"
                }
                """;

        mockMvc.perform(post("/api/v1/slots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.errors.slotCode").exists());
    }

    @Test
    void getSlots_shouldReturn200List() throws Exception {
        when(slotService.findAll())
                .thenReturn(List.of(new SlotResponseDto(1L, "2026-06-15_09:00", "SALLE-A1", true)));

        mockMvc.perform(get("/api/v1/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomCode").value("SALLE-A1"));
    }
}