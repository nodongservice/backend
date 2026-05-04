ALTER TABLE onboarding_profile
    ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_name = 'onboarding_profile'
          AND constraint_name = 'uk_onboarding_profile_user'
    ) THEN
        ALTER TABLE onboarding_profile DROP CONSTRAINT uk_onboarding_profile_user;
    END IF;
END $$;

-- 기존 단건 구조 데이터는 사용자별 기본 프로필로 승격한다.
UPDATE onboarding_profile
SET is_default = TRUE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_onboarding_profile_user_default
    ON onboarding_profile (user_id)
    WHERE is_default = TRUE;

CREATE INDEX IF NOT EXISTS idx_onboarding_profile_user_default
    ON onboarding_profile (user_id, is_default);
