ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE;

UPDATE app_user
SET status = 'ACTIVE'
WHERE status IS NULL;

ALTER TABLE app_user
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_app_user_status'
    ) THEN
        ALTER TABLE app_user
            ADD CONSTRAINT ck_app_user_status
                CHECK (status IN ('ACTIVE', 'DELETED'));
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_app_user_status_signup_completed
    ON app_user (status, signup_completed);
