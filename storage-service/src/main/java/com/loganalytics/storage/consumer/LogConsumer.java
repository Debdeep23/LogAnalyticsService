package com.loganalytics.storage.consumer;

import com.loganalytics.storage.dto.LogEvent;
import com.loganalytics.storage.service.LogStorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class LogConsumer {

    private final LogStorageService logStorageService;
    private final Counter messagesConsumedCounter;

    public LogConsumer(LogStorageService logStorageService, MeterRegistry meterRegistry) {
        this.logStorageService = logStorageService;
        this.messagesConsumedCounter = Counter.builder("kafka.messages.consumed")
                .description("Total number of Kafka messages consumed")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${kafka.topic.logs}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeLogs(List<LogEvent> events) {
        log.debug("Received batch of {} log events from Kafka", events.size());
        
        try {
            messagesConsumedCounter.increment(events.size());
            logStorageService.storeLogs(events);
            log.debug("Successfully processed batch of {} log events", events.size());
        } catch (Exception e) {
            log.error("Error processing log batch", e);
            throw e; // Re-throw to trigger Kafka retry/DLQ
        }
    }
}

