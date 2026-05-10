-- app_user.email을 소셜 계정 기준 필수값으로 강제한다.
-- 기존 null 데이터는 비식별 이메일로 선보정해 NOT NULL 제약 적용 실패를 방지한다.
UPDATE app_user
SET email = 'deleted-user-' || id || '@bridgework.local'
WHERE email IS NULL;

ALTER TABLE app_user
    ALTER COLUMN email SET NOT NULL;
