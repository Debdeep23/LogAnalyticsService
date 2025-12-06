package com.loganalytics.ingestion.controller;

import com.loganalytics.ingestion.dto.LogEvent;
import com.loganalytics.ingestion.dto.LogResponse;
import com.loganalytics.ingestion.service.LogProducerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogProducerService logProducerService;

    @PostMapping
    public ResponseEntity<LogResponse> ingestLog(@Valid @RequestBody LogEvent logEvent) {
        log.debug("Received log event from service: {}", logEvent.getServiceName());
        
        String eventId = logProducerService.publishLog(logEvent);
        
        return ResponseEntity.ok(LogResponse.builder()
                .status("accepted")
                .message("Log event accepted for processing")
                .eventId(eventId)
                .build());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}

