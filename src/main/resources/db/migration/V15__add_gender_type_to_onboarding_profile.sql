ALTER TABLE onboarding_profile
    ADD COLUMN IF NOT EXISTS gender_type VARCHAR(20);

UPDATE onboarding_profile
SET gender_type = 'NOT_DISCLOSED'
WHERE gender_type IS NULL
   OR btrim(gender_type) = '';

ALTER TABLE onboarding_profile
    ALTER COLUMN gender_type SET DEFAULT 'NOT_DISCLOSED',
    ALTER COLUMN gender_type SET NOT NULL;

ALTER TABLE onboarding_profile
    ADD CONSTRAINT chk_onboarding_profile_gender_type
        CHECK (gender_type IN ('MALE', 'FEMALE', 'OTHER', 'NOT_DISCLOSED')) NOT VALID;
