ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS withdrawal_requested_at TIMESTAMP WITH TIME ZONE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_app_user_status'
    ) THEN
        ALTER TABLE app_user DROP CONSTRAINT ck_app_user_status;
    END IF;

    ALTER TABLE app_user
        ADD CONSTRAINT ck_app_user_status
            CHECK (status IN ('ACTIVE', 'PENDING_DELETION', 'DELETED'));
END
$$;

CREATE INDEX IF NOT EXISTS idx_app_user_withdrawal_pending
    ON app_user (status, withdrawal_requested_at);
