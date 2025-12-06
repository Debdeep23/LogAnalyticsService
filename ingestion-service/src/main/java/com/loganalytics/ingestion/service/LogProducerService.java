package com.loganalytics.ingestion.service;

import com.loganalytics.ingestion.dto.LogEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class LogProducerService {

    private final KafkaTemplate<String, LogEvent> kafkaTemplate;
    private final String topic;
    private final Counter logsIngestedCounter;
    private final Counter logsFailedCounter;
    private final Timer publishTimer;

    public LogProducerService(
            KafkaTemplate<String, LogEvent> kafkaTemplate,
            @Value("${kafka.topic.logs}") String topic,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.logsIngestedCounter = Counter.builder("logs.ingested")
                .description("Total number of logs ingested")
                .register(meterRegistry);
        this.logsFailedCounter = Counter.builder("logs.failed")
                .description("Total number of failed log ingestions")
                .register(meterRegistry);
        this.publishTimer = Timer.builder("logs.publish.duration")
                .description("Time taken to publish logs to Kafka")
                .register(meterRegistry);
    }

    public String publishLog(LogEvent logEvent) {
        String eventId = UUID.randomUUID().toString();
        String key = logEvent.getServiceName();

        return publishTimer.record(() -> {
            try {
                CompletableFuture<SendResult<String, LogEvent>> future = 
                    kafkaTemplate.send(topic, key, logEvent);

                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish log event: {}", eventId, ex);
                        logsFailedCounter.increment();
                    } else {
                        log.debug("Published log event: {} to partition: {} with offset: {}",
                                eventId,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                        logsIngestedCounter.increment();
                    }
                });

                return eventId;
            } catch (Exception e) {
                log.error("Error publishing log event: {}", eventId, e);
                logsFailedCounter.increment();
                throw new RuntimeException("Failed to publish log to Kafka", e);
            }
        });
    }
}

