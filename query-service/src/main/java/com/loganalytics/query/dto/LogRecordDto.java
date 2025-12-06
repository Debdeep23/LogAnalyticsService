package com.loganalytics.query.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogRecordDto {
    private Long id;
    private OffsetDateTime timestamp;
    private String serviceName;
    private String level;
    private String message;
    private String host;
    private String traceId;
    private Map<String, Object> extra;
}

