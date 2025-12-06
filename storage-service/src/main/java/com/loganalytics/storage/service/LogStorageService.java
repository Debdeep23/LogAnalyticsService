package com.loganalytics.storage.service;

import com.loganalytics.storage.dto.LogEvent;
import com.loganalytics.storage.entity.LogRecord;
import com.loganalytics.storage.repository.LogRecordRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LogStorageService {

    private final LogRecordRepository logRecordRepository;
    private final ObjectStorageService objectStorageService;
    private final Counter logsStoredCounter;
    private final Timer batchInsertTimer;

    public LogStorageService(
            LogRecordRepository logRecordRepository,
            ObjectStorageService objectStorageService,
            MeterRegistry meterRegistry) {
        this.logRecordRepository = logRecordRepository;
        this.objectStorageService = objectStorageService;
        this.logsStoredCounter = Counter.builder("logs.stored")
                .description("Total number of logs stored in Postgres")
                .register(meterRegistry);
        this.batchInsertTimer = Timer.builder("logs.batch.insert.duration")
                .description("Time taken to batch insert logs")
                .register(meterRegistry);
    }

    @PostConstruct
    public void init() {
        log.info("LogStorageService initialized with storage provider: {}", 
                objectStorageService.getProviderName());
    }

    @Transactional
    public void storeLogs(List<LogEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        batchInsertTimer.record(() -> {
            try {
                // Store raw logs to object storage (MinIO or AWS S3)
                String rawS3Key = objectStorageService.storeRawLogs(events);

                // Convert and save to Postgres
                List<LogRecord> records = events.stream()
                        .map(event -> mapToLogRecord(event, rawS3Key))
                        .collect(Collectors.toList());

                logRecordRepository.saveAll(records);
                logsStoredCounter.increment(records.size());
                
                log.debug("Stored {} log records to Postgres with {} key: {}", 
                        records.size(), objectStorageService.getProviderName(), rawS3Key);
            } catch (Exception e) {
                log.error("Failed to store logs", e);
                throw new RuntimeException("Failed to store logs", e);
            }
        });
    }

    private LogRecord mapToLogRecord(LogEvent event, String rawS3Key) {
        return LogRecord.builder()
                .timestamp(parseTimestamp(event.getTimestamp()))
                .serviceName(event.getServiceName())
                .level(event.getLevel())
                .message(event.getMessage())
                .host(event.getHost())
                .traceId(event.getTraceId())
                .rawS3Key(rawS3Key)
                .extra(event.getExtra())
                .build();
    }

    private OffsetDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return OffsetDateTime.now();
        }

        try {
            // Try ISO-8601 format first
            return OffsetDateTime.parse(timestamp);
        } catch (DateTimeParseException e1) {
            try {
                // Try parsing as epoch milliseconds
                long epochMilli = Long.parseLong(timestamp);
                return OffsetDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(epochMilli),
                        java.time.ZoneOffset.UTC
                );
            } catch (NumberFormatException e2) {
                log.warn("Could not parse timestamp: {}, using current time", timestamp);
                return OffsetDateTime.now();
            }
        }
    }
}
