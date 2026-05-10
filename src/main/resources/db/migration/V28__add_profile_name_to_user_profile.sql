ALTER TABLE IF EXISTS public.user_profile
    ADD COLUMN IF NOT EXISTS profile_name VARCHAR(100);

UPDATE public.user_profile
SET profile_name = CASE
    WHEN is_default THEN '기본 생성 프로필'
    ELSE '프로필 ' || id
END
WHERE profile_name IS NULL OR BTRIM(profile_name) = '';

ALTER TABLE IF EXISTS public.user_profile
    ALTER COLUMN profile_name SET NOT NULL;
