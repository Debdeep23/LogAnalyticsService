package com.loganalytics.ingestion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalytics.ingestion.dto.LogEvent;
import com.loganalytics.ingestion.service.LogProducerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LogController.class)
class LogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LogProducerService logProducerService;

    @Test
    void shouldAcceptValidLogEvent() throws Exception {
        LogEvent logEvent = LogEvent.builder()
                .timestamp("2024-01-15T10:30:00Z")
                .serviceName("test-service")
                .level("INFO")
                .message("Test log message")
                .host("server-01")
                .build();

        String eventId = UUID.randomUUID().toString();
        when(logProducerService.publishLog(any(LogEvent.class))).thenReturn(eventId);

        mockMvc.perform(post("/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logEvent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.eventId").value(eventId));
    }

    @Test
    void shouldRejectLogEventWithoutTimestamp() throws Exception {
        LogEvent logEvent = LogEvent.builder()
                .serviceName("test-service")
                .level("INFO")
                .message("Test log message")
                .build();

        mockMvc.perform(post("/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logEvent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("timestamp"));
    }

    @Test
    void shouldRejectLogEventWithInvalidLevel() throws Exception {
        LogEvent logEvent = LogEvent.builder()
                .timestamp("2024-01-15T10:30:00Z")
                .serviceName("test-service")
                .level("INVALID")
                .message("Test log message")
                .build();

        mockMvc.perform(post("/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logEvent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("level"));
    }

    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(post("/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }
}

