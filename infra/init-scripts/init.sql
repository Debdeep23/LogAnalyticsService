-- Initialize the logs database schema

-- Create the logs table
CREATE TABLE IF NOT EXISTS log_records (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    level VARCHAR(10) NOT NULL,
    message TEXT NOT NULL,
    host VARCHAR(255),
    trace_id VARCHAR(255),
    raw_s3_key VARCHAR(500),
    extra JSONB,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_log_records_timestamp ON log_records(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_log_records_service_name ON log_records(service_name);
CREATE INDEX IF NOT EXISTS idx_log_records_level ON log_records(level);
CREATE INDEX IF NOT EXISTS idx_log_records_service_level ON log_records(service_name, level);
CREATE INDEX IF NOT EXISTS idx_log_records_timestamp_service ON log_records(timestamp DESC, service_name);
CREATE INDEX IF NOT EXISTS idx_log_records_trace_id ON log_records(trace_id) WHERE trace_id IS NOT NULL;

-- Create a composite index for time-range queries with filters
CREATE INDEX IF NOT EXISTS idx_log_records_time_service_level 
    ON log_records(timestamp DESC, service_name, level);

-- Add comment to table
COMMENT ON TABLE log_records IS 'Stores structured log events from all services';

