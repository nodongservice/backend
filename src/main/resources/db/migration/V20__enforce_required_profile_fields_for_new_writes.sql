DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_user_profile_birth_date_required'
    ) THEN
        ALTER TABLE public.user_profile
            ADD CONSTRAINT chk_user_profile_birth_date_required
                CHECK (birth_date IS NOT NULL) NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_user_profile_detail_address_required'
    ) THEN
        ALTER TABLE public.user_profile
            ADD CONSTRAINT chk_user_profile_detail_address_required
                CHECK (detail_address IS NOT NULL AND btrim(detail_address) <> '') NOT VALID;
    END IF;
END $$;
