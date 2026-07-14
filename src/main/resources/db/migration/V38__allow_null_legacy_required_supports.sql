-- V37에서 민감정보를 user_profile_sensitive_info로 분리한 뒤에도 남아 있던
-- 레거시 컬럼의 NOT NULL 제약을 해제해 신규 user_profile 행 생성을 허용한다.
ALTER TABLE IF EXISTS public.user_profile
    ALTER COLUMN required_supports_json DROP NOT NULL;
