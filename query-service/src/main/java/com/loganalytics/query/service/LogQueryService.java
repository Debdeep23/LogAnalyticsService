package com.loganalytics.query.service;

import com.loganalytics.query.dto.*;
import com.loganalytics.query.entity.LogRecord;
import com.loganalytics.query.repository.LogRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class LogQueryService {

    private final LogRecordRepository logRecordRepository;
    private final Timer queryTimer;
    private final int defaultPageSize;
    private final int maxPageSize;

    public LogQueryService(
            LogRecordRepository logRecordRepository,
            MeterRegistry meterRegistry,
            @Value("${query.default-page-size:20}") int defaultPageSize,
            @Value("${query.max-page-size:100}") int maxPageSize) {
        this.logRecordRepository = logRecordRepository;
        this.defaultPageSize = defaultPageSize;
        this.maxPageSize = maxPageSize;
        this.queryTimer = Timer.builder("logs.query.duration")
                .description("Time taken to query logs")
                .register(meterRegistry);
    }

    public PagedResponse<LogRecordDto> queryLogs(
            String serviceName,
            String level,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size) {

        return queryTimer.record(() -> {
            int pageSize = Math.min(size > 0 ? size : defaultPageSize, maxPageSize);
            Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "timestamp"));

            // Set defaults for time range if not provided
            OffsetDateTime fromTime = from != null ? from : OffsetDateTime.now().minusDays(7);
            OffsetDateTime toTime = to != null ? to : OffsetDateTime.now();

            Specification<LogRecord> spec = buildSpecification(serviceName, level, fromTime, toTime);
            Page<LogRecord> resultPage = logRecordRepository.findAll(spec, pageable);

            List<LogRecordDto> dtos = resultPage.getContent().stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            return PagedResponse.<LogRecordDto>builder()
                    .content(dtos)
                    .page(resultPage.getNumber())
                    .size(resultPage.getSize())
                    .totalElements(resultPage.getTotalElements())
                    .totalPages(resultPage.getTotalPages())
                    .first(resultPage.isFirst())
                    .last(resultPage.isLast())
                    .build();
        });
    }

    public List<ErrorCountDto> getErrorsPerService(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime fromTime = from != null ? from : OffsetDateTime.now().minusDays(7);
        OffsetDateTime toTime = to != null ? to : OffsetDateTime.now();

        List<Object[]> results = logRecordRepository.countErrorsPerService(fromTime, toTime);
        
        return results.stream()
                .map(row -> ErrorCountDto.builder()
                        .serviceName((String) row[0])
                        .errorCount((Long) row[1])
                        .build())
                .collect(Collectors.toList());
    }

    public List<LevelDistributionDto> getLevelDistribution(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime fromTime = from != null ? from : OffsetDateTime.now().minusDays(7);
        OffsetDateTime toTime = to != null ? to : OffsetDateTime.now();

        List<Object[]> results = logRecordRepository.countByLevel(fromTime, toTime);
        
        return results.stream()
                .map(row -> LevelDistributionDto.builder()
                        .level((String) row[0])
                        .count((Long) row[1])
                        .build())
                .collect(Collectors.toList());
    }

    public List<CountPerMinuteDto> getCountPerMinute(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime fromTime = from != null ? from : OffsetDateTime.now().minusHours(1);
        OffsetDateTime toTime = to != null ? to : OffsetDateTime.now();

        List<Object[]> results = logRecordRepository.countPerMinute(fromTime, toTime);
        
        return results.stream()
                .map(row -> {
                    Timestamp ts = (Timestamp) row[0];
                    return CountPerMinuteDto.builder()
                            .minute(ts.toInstant().atOffset(java.time.ZoneOffset.UTC))
                            .count((Long) row[1])
                            .build();
                })
                .collect(Collectors.toList());
    }

    private Specification<LogRecord> buildSpecification(
            String serviceName,
            String level,
            OffsetDateTime from,
            OffsetDateTime to) {
        
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by time range
            predicates.add(criteriaBuilder.between(root.get("timestamp"), from, to));

            if (serviceName != null && !serviceName.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("serviceName"), serviceName));
            }

            if (level != null && !level.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("level"), level));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private LogRecordDto toDto(LogRecord entity) {
        return LogRecordDto.builder()
                .id(entity.getId())
                .timestamp(entity.getTimestamp())
                .serviceName(entity.getServiceName())
                .level(entity.getLevel())
                .message(entity.getMessage())
                .host(entity.getHost())
                .traceId(entity.getTraceId())
                .extra(entity.getExtra())
                .build();
    }
}

