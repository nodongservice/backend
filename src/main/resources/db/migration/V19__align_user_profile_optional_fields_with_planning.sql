ALTER TABLE IF EXISTS public.user_profile
    ALTER COLUMN desired_job DROP NOT NULL,
    ALTER COLUMN commute_range DROP NOT NULL,
    ALTER COLUMN career_summary DROP NOT NULL,
    ALTER COLUMN education_summary DROP NOT NULL,
    ALTER COLUMN employment_type_summary DROP NOT NULL,
    ALTER COLUMN work_availability DROP NOT NULL,
    ALTER COLUMN motivation DROP NOT NULL;
