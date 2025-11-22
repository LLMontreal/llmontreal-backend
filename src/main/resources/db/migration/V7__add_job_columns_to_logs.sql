ALTER TABLE log_api_calls
ADD COLUMN job_latency_ms BIGINT;

ALTER TABLE log_api_calls
ADD COLUMN job_status_code INTEGER;

ALTER TABLE log_api_calls
ADD COLUMN job_error_message TEXT;