ALTER TABLE IF EXISTS public.user_profile
    DROP COLUMN IF EXISTS disability_yn;

-- 과거 스키마명을 사용하는 잔여 환경도 안전하게 정리한다.
ALTER TABLE IF EXISTS public.onboarding_profile
    DROP COLUMN IF EXISTS disability_yn;
