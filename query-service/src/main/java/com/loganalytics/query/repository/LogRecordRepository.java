package com.loganalytics.query.repository;

import com.loganalytics.query.entity.LogRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface LogRecordRepository extends JpaRepository<LogRecord, Long>, JpaSpecificationExecutor<LogRecord> {

    Page<LogRecord> findByTimestampBetween(
            OffsetDateTime from, 
            OffsetDateTime to, 
            Pageable pageable);

    Page<LogRecord> findByServiceNameAndTimestampBetween(
            String serviceName, 
            OffsetDateTime from, 
            OffsetDateTime to, 
            Pageable pageable);

    Page<LogRecord> findByLevelAndTimestampBetween(
            String level, 
            OffsetDateTime from, 
            OffsetDateTime to, 
            Pageable pageable);

    Page<LogRecord> findByServiceNameAndLevelAndTimestampBetween(
            String serviceName, 
            String level, 
            OffsetDateTime from, 
            OffsetDateTime to, 
            Pageable pageable);

    @Query("SELECT l.serviceName as serviceName, COUNT(l) as errorCount " +
           "FROM LogRecord l " +
           "WHERE l.level = 'ERROR' AND l.timestamp BETWEEN :from AND :to " +
           "GROUP BY l.serviceName " +
           "ORDER BY COUNT(l) DESC")
    List<Object[]> countErrorsPerService(
            @Param("from") OffsetDateTime from, 
            @Param("to") OffsetDateTime to);

    @Query("SELECT l.level as level, COUNT(l) as count " +
           "FROM LogRecord l " +
           "WHERE l.timestamp BETWEEN :from AND :to " +
           "GROUP BY l.level " +
           "ORDER BY COUNT(l) DESC")
    List<Object[]> countByLevel(
            @Param("from") OffsetDateTime from, 
            @Param("to") OffsetDateTime to);

    @Query(value = "SELECT date_trunc('minute', timestamp) as minute, COUNT(*) as count " +
           "FROM log_records " +
           "WHERE timestamp BETWEEN :from AND :to " +
           "GROUP BY date_trunc('minute', timestamp) " +
           "ORDER BY minute", 
           nativeQuery = true)
    List<Object[]> countPerMinute(
            @Param("from") OffsetDateTime from, 
            @Param("to") OffsetDateTime to);
}

