package com.loganalytics.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEvent {

    @NotBlank(message = "Timestamp is required")
    private String timestamp;

    @NotBlank(message = "Service name is required")
    @Size(max = 255, message = "Service name must be less than 255 characters")
    private String serviceName;

    @NotNull(message = "Log level is required")
    @Pattern(regexp = "^(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)$", 
             message = "Level must be one of: TRACE, DEBUG, INFO, WARN, ERROR, FATAL")
    private String level;

    @NotBlank(message = "Message is required")
    @Size(max = 65535, message = "Message must be less than 65535 characters")
    private String message;

    @Size(max = 255, message = "Host must be less than 255 characters")
    private String host;

    @Size(max = 255, message = "Trace ID must be less than 255 characters")
    private String traceId;

    private Map<String, Object> extra;
}

