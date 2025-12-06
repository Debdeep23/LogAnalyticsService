-- Create the logs table with proper indexing
CREATE TABLE IF NOT EXISTS log_records (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    level VARCHAR(10) NOT NULL,
    message TEXT NOT NULL,
    host VARCHAR(255),
    trace_id VARCHAR(128),
    raw_object_key VARCHAR(512),
    extra JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_log_records_timestamp ON log_records(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_log_records_service_name ON log_records(service_name);
CREATE INDEX IF NOT EXISTS idx_log_records_level ON log_records(level);
CREATE INDEX IF NOT EXISTS idx_log_records_trace_id ON log_records(trace_id) WHERE trace_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_log_records_service_level ON log_records(service_name, level);
CREATE INDEX IF NOT EXISTS idx_log_records_timestamp_service ON log_records(timestamp DESC, service_name);

-- Create a partial index for error logs (frequently queried)
CREATE INDEX IF NOT EXISTS idx_log_records_errors ON log_records(timestamp DESC, service_name) 
    WHERE level IN ('ERROR', 'FATAL');

-- Add a comment describing the table
COMMENT ON TABLE log_records IS 'Structured log records ingested from various services';
COMMENT ON COLUMN log_records.raw_object_key IS 'Reference to raw log in MinIO/S3';
COMMENT ON COLUMN log_records.extra IS 'Additional metadata as JSON';

