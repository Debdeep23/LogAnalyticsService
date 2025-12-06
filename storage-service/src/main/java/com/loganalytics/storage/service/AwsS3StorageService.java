package com.loganalytics.storage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loganalytics.storage.dto.LogEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AWS S3 implementation of ObjectStorageService.
 * Used in production environments with real AWS S3.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "aws-s3")
public class AwsS3StorageService implements ObjectStorageService {

    private final ObjectMapper objectMapper;
    private final S3Client s3Client;
    private final String bucket;
    private final String region;
    private final Counter rawLogsStoredCounter;

    public AwsS3StorageService(
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            org.springframework.core.env.Environment env) {
        this.objectMapper = objectMapper;
        this.bucket = env.getProperty("aws.s3.bucket", "logs-raw");
        this.region = env.getProperty("aws.region", "us-east-1");
        
        String accessKey = env.getProperty("aws.access-key-id");
        String secretKey = env.getProperty("aws.secret-access-key");
        
        // Use explicit credentials if provided, otherwise use default credential chain
        if (accessKey != null && secretKey != null && !accessKey.isEmpty()) {
            this.s3Client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
            log.info("AWS S3 client initialized with explicit credentials");
        } else {
            this.s3Client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            log.info("AWS S3 client initialized with default credential chain (IAM role, env vars, etc.)");
        }
        
        this.rawLogsStoredCounter = Counter.builder("logs.raw.stored")
                .tag("provider", "aws-s3")
                .description("Total number of raw logs stored in AWS S3")
                .register(meterRegistry);
    }

    @PostConstruct
    public void init() {
        log.info("AWS S3 Storage Service initialized - Bucket: {}, Region: {}", bucket, region);
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

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType("application/x-ndjson")
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(content));

            rawLogsStoredCounter.increment(events.size());
            log.debug("Stored {} raw logs to AWS S3: s3://{}/{}", events.size(), bucket, objectKey);
            
            return objectKey;
        } catch (Exception e) {
            log.error("Failed to store raw logs to AWS S3", e);
            throw new RuntimeException("Failed to store raw logs to AWS S3", e);
        }
    }

    @Override
    public String getProviderName() {
        return "aws-s3";
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
        return String.format("logs/%s/%s", datePrefix, filename);
    }
}

