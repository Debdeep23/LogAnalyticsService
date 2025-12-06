package com.loganalytics.query.controller;

import com.loganalytics.query.dto.*;
import com.loganalytics.query.service.LogQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LogQueryController.class)
class LogQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LogQueryService logQueryService;

    @Test
    void shouldQueryLogs() throws Exception {
        List<LogRecordDto> logs = Arrays.asList(
                LogRecordDto.builder()
                        .id(1L)
                        .timestamp(OffsetDateTime.now())
                        .serviceName("test-service")
                        .level("INFO")
                        .message("Test message")
                        .build()
        );

        PagedResponse<LogRecordDto> response = PagedResponse.<LogRecordDto>builder()
                .content(logs)
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .build();

        when(logQueryService.queryLogs(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(response);

        mockMvc.perform(get("/logs")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].serviceName").value("test-service"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldGetErrorsPerService() throws Exception {
        List<ErrorCountDto> errors = Arrays.asList(
                ErrorCountDto.builder()
                        .serviceName("service-a")
                        .errorCount(10L)
                        .build(),
                ErrorCountDto.builder()
                        .serviceName("service-b")
                        .errorCount(5L)
                        .build()
        );

        when(logQueryService.getErrorsPerService(any(), any())).thenReturn(errors);

        mockMvc.perform(get("/stats/errors-per-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].serviceName").value("service-a"))
                .andExpect(jsonPath("$[0].errorCount").value(10));
    }

    @Test
    void shouldGetLevelDistribution() throws Exception {
        List<LevelDistributionDto> levels = Arrays.asList(
                LevelDistributionDto.builder().level("INFO").count(100L).build(),
                LevelDistributionDto.builder().level("ERROR").count(10L).build()
        );

        when(logQueryService.getLevelDistribution(any(), any())).thenReturn(levels);

        mockMvc.perform(get("/stats/levels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].level").value("INFO"))
                .andExpect(jsonPath("$[0].count").value(100));
    }
}

