CREATE TABLE log_api_calls (
    id BIGSERIAL PRIMARY KEY,
    correlation_id VARCHAR(255) NOT NULL,
    event_timestamp TIMESTAMP NOT NULL,
    latency_ms BIGINT,
    endpoint VARCHAR(255),
    status_code INTEGER,
    ip_address VARCHAR(50)
);

CREATE INDEX idx_log_api_calls_correlation_id
ON log_api_calls (correlation_id);