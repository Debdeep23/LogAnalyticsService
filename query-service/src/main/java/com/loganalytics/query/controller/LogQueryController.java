package com.loganalytics.query.controller;

import com.loganalytics.query.dto.*;
import com.loganalytics.query.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class LogQueryController {

    private final LogQueryService logQueryService;

    @GetMapping("/logs")
    public ResponseEntity<PagedResponse<LogRecordDto>> queryLogs(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Query logs: serviceName={}, level={}, from={}, to={}, page={}, size={}",
                serviceName, level, from, to, page, size);

        PagedResponse<LogRecordDto> response = logQueryService.queryLogs(
                serviceName, level, from, to, page, size);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/errors-per-service")
    public ResponseEntity<List<ErrorCountDto>> getErrorsPerService(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        log.debug("Get errors per service: from={}, to={}", from, to);

        List<ErrorCountDto> response = logQueryService.getErrorsPerService(from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/levels")
    public ResponseEntity<List<LevelDistributionDto>> getLevelDistribution(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        log.debug("Get level distribution: from={}, to={}", from, to);

        List<LevelDistributionDto> response = logQueryService.getLevelDistribution(from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/count-per-minute")
    public ResponseEntity<List<CountPerMinuteDto>> getCountPerMinute(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        log.debug("Get count per minute: from={}, to={}", from, to);

        List<CountPerMinuteDto> response = logQueryService.getCountPerMinute(from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}

