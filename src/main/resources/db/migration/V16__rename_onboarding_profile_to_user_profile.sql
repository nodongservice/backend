DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'onboarding_profile'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_name = 'user_profile'
    ) THEN
        ALTER TABLE onboarding_profile RENAME TO user_profile;
    END IF;
END $$;

ALTER INDEX IF EXISTS idx_onboarding_profile_updated_at RENAME TO idx_user_profile_updated_at;
ALTER INDEX IF EXISTS idx_onboarding_profile_user_id RENAME TO idx_user_profile_user_id;
ALTER INDEX IF EXISTS uk_onboarding_profile_user_default RENAME TO uk_user_profile_user_default;
ALTER INDEX IF EXISTS idx_onboarding_profile_user_default RENAME TO idx_user_profile_user_default;

ALTER TABLE IF EXISTS user_profile
    RENAME CONSTRAINT fk_onboarding_profile_user TO fk_user_profile_user;

ALTER TABLE IF EXISTS user_profile
    RENAME CONSTRAINT chk_onboarding_profile_gender_type TO chk_user_profile_gender_type;
