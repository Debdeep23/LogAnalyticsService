package com.loganalytics.storage.dto;

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
    private String timestamp;
    private String serviceName;
    private String level;
    private String message;
    private String host;
    private String traceId;
    private Map<String, Object> extra;
}

