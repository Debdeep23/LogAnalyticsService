package com.loganalytics.storage.service;

import com.loganalytics.storage.dto.LogEvent;

import java.util.List;

/**
 * Interface for object storage operations.
 * Implementations can use MinIO (local) or AWS S3 (cloud).
 */
public interface ObjectStorageService {
    
    /**
     * Store raw log events as a JSONL file.
     * 
     * @param events List of log events to store
     * @return The object key where the logs were stored
     */
    String storeRawLogs(List<LogEvent> events);
    
    /**
     * Get the storage provider name (e.g., "minio" or "aws-s3")
     */
    String getProviderName();
}

