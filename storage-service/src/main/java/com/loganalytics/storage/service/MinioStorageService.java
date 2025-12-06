package com.loganalytics.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalytics.storage.dto.LogEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.minio.PutObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MinIO implementation of ObjectStorageService.
 * Used for local development with S3-compatible MinIO.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "minio", matchIfMissing = true)
public class MinioStorageService implements ObjectStorageService {

    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;
    private final String bucket;
    private final Counter rawLogsStoredCounter;

    public MinioStorageService(
            MinioClient minioClient,
            ObjectMapper objectMapper,
            @Value("${minio.bucket}") String bucket,
            MeterRegistry meterRegistry) {
        this.minioClient = minioClient;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
        this.rawLogsStoredCounter = Counter.builder("logs.raw.stored")
                .tag("provider", "minio")
                .description("Total number of raw logs stored in MinIO")
                .register(meterRegistry);
    }

    @PostConstruct
    public void init() {
        log.info("MinIO Storage Service initialized - Bucket: {}", bucket);
    }

    @Override
    public String storeRawLogs(List<LogEvent> events) {
        if (events == null || events.isEmpty()) {
            return null;
        }

        try {
            String jsonlContent = events.stream()
                    .map(this::toJsonLine)
                    .collect(Collectors.joining("\n"));

            String objectKey = generateObjectKey();
            byte[] content = jsonlContent.getBytes(StandardCharsets.UTF_8);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(content), content.length, -1)
                            .contentType("application/x-ndjson")
                            .build()
            );

            rawLogsStoredCounter.increment(events.size());
            log.debug("Stored {} raw logs to MinIO: {}", events.size(), objectKey);
            
            return objectKey;
        } catch (Exception e) {
            log.error("Failed to store raw logs to MinIO", e);
            throw new RuntimeException("Failed to store raw logs", e);
        }
    }

    @Override
    public String getProviderName() {
        return "minio";
    }

    private String toJsonLine(LogEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize log event", e);
            return "{}";
        }
    }

    private String generateObjectKey() {
        LocalDate today = LocalDate.now();
        String datePrefix = today.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UUID.randomUUID().toString() + ".jsonl";
        return String.format("%s/%s", datePrefix, filename);
    }
}
