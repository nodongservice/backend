ALTER TABLE pd_kepad_recruitment
    ADD COLUMN IF NOT EXISTS posting_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS status_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

UPDATE pd_kepad_recruitment
SET posting_status = COALESCE(NULLIF(posting_status, ''), 'ACTIVE'),
    status_updated_at = COALESCE(status_updated_at, updated_at, NOW())
WHERE posting_status IS DISTINCT FROM 'ACTIVE'
   OR status_updated_at IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_pd_kepad_recruitment_posting_status'
    ) THEN
        ALTER TABLE pd_kepad_recruitment
            ADD CONSTRAINT ck_pd_kepad_recruitment_posting_status
            CHECK (posting_status IN ('ACTIVE', 'CLOSED'));
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_pd_kepad_recruitment_posting_status
    ON pd_kepad_recruitment (posting_status);
CREATE INDEX IF NOT EXISTS idx_pd_kepad_recruitment_popular_lookup
    ON pd_kepad_recruitment (posting_status, reg_dt DESC, updated_at DESC, id DESC);

ALTER TABLE public_data_record
    ADD COLUMN IF NOT EXISTS sync_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS status_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

UPDATE public_data_record
SET sync_status = COALESCE(NULLIF(sync_status, ''), 'ACTIVE'),
    status_updated_at = COALESCE(status_updated_at, updated_at, NOW())
WHERE sync_status IS DISTINCT FROM 'ACTIVE'
   OR status_updated_at IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_public_data_record_sync_status'
    ) THEN
        ALTER TABLE public_data_record
            ADD CONSTRAINT ck_public_data_record_sync_status
            CHECK (sync_status IN ('ACTIVE', 'CLOSED'));
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_public_data_record_sync_status
    ON public_data_record (source_type, sync_status, updated_at DESC);

CREATE TABLE IF NOT EXISTS job_scrap (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    posting_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_job_scrap_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_scrap_posting FOREIGN KEY (posting_id) REFERENCES pd_kepad_recruitment (id) ON DELETE RESTRICT,
    CONSTRAINT uk_job_scrap_user_posting UNIQUE (user_id, posting_id)
);

CREATE INDEX IF NOT EXISTS idx_job_scrap_posting_id ON job_scrap (posting_id);
CREATE INDEX IF NOT EXISTS idx_job_scrap_user_created_at ON job_scrap (user_id, created_at DESC, id DESC);
