CREATE TABLE IF NOT EXISTS public_data_record (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(64) NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    payload_json TEXT NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    raw_fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_public_data_source_external UNIQUE (source_type, external_id)
);

CREATE INDEX IF NOT EXISTS idx_public_data_record_source_type ON public_data_record (source_type);
CREATE INDEX IF NOT EXISTS idx_public_data_record_updated_at ON public_data_record (updated_at DESC);

CREATE TABLE IF NOT EXISTS public_data_sync_log (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(64) NOT NULL,
    request_source VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    processed_count INTEGER NOT NULL DEFAULT 0,
    new_count INTEGER NOT NULL DEFAULT 0,
    updated_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_public_data_sync_log_started_at ON public_data_sync_log (started_at DESC);
CREATE INDEX IF NOT EXISTS idx_public_data_sync_log_source_type ON public_data_sync_log (source_type);

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NULL,
    locked_at TIMESTAMP(3) NULL,
    locked_by VARCHAR(255) NULL,
    PRIMARY KEY (name)
);
