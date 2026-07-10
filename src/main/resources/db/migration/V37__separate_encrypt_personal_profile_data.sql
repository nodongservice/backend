CREATE TABLE IF NOT EXISTS user_profile_private_details (
    profile_id BIGINT PRIMARY KEY,
    full_name TEXT NOT NULL,
    contact_phone TEXT NOT NULL,
    contact_email_override TEXT,
    birth_date TEXT,
    gender_type TEXT,
    detail_address TEXT,
    emergency_contact TEXT,
    home_lat TEXT,
    home_lng TEXT,
    home_geocoded_address TEXT,
    CONSTRAINT fk_user_profile_private_details_profile
        FOREIGN KEY (profile_id) REFERENCES user_profile (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_profile_sensitive_info (
    profile_id BIGINT PRIMARY KEY,
    required_supports_json TEXT NOT NULL,
    disability_type TEXT,
    disability_severity TEXT,
    disability_registered_yn TEXT,
    sensitive_info_consent_yn TEXT,
    disability_description TEXT,
    assistive_devices TEXT,
    work_support_requirements TEXT,
    CONSTRAINT fk_user_profile_sensitive_info_profile
        FOREIGN KEY (profile_id) REFERENCES user_profile (id) ON DELETE CASCADE
);

INSERT INTO user_profile_private_details (
    profile_id,
    full_name,
    contact_phone,
    contact_email_override,
    birth_date,
    gender_type,
    detail_address,
    emergency_contact,
    home_lat,
    home_lng,
    home_geocoded_address
)
SELECT
    profile.id,
    COALESCE(profile.full_name, '탈퇴회원'),
    COALESCE(profile.contact_phone, '00000000000'),
    CASE
        WHEN lower(COALESCE(profile.contact_email, '')) = lower(COALESCE(app_user.email, '')) THEN NULL
        ELSE profile.contact_email
    END,
    CASE WHEN profile.birth_date IS NULL THEN NULL ELSE profile.birth_date::TEXT END,
    profile.gender_type,
    profile.detail_address,
    profile.emergency_contact,
    CASE WHEN profile.home_lat IS NULL THEN NULL ELSE profile.home_lat::TEXT END,
    CASE WHEN profile.home_lng IS NULL THEN NULL ELSE profile.home_lng::TEXT END,
    profile.home_geocoded_address
FROM user_profile profile
JOIN app_user ON app_user.id = profile.user_id
ON CONFLICT (profile_id) DO NOTHING;

INSERT INTO user_profile_sensitive_info (
    profile_id,
    required_supports_json,
    disability_type,
    disability_severity,
    disability_registered_yn,
    sensitive_info_consent_yn,
    disability_description,
    assistive_devices,
    work_support_requirements
)
SELECT
    id,
    COALESCE(required_supports_json, '[]'),
    disability_type,
    disability_severity,
    CASE
        WHEN disability_registered_yn IS NULL THEN NULL
        WHEN disability_registered_yn THEN 'true'
        ELSE 'false'
    END,
    CASE
        WHEN sensitive_info_consent_yn IS NULL THEN NULL
        WHEN sensitive_info_consent_yn THEN 'true'
        ELSE 'false'
    END,
    disability_description,
    assistive_devices,
    work_support_requirements
FROM user_profile
ON CONFLICT (profile_id) DO NOTHING;

ALTER TABLE user_profile ALTER COLUMN full_name DROP NOT NULL;
ALTER TABLE user_profile ALTER COLUMN contact_phone DROP NOT NULL;
ALTER TABLE user_profile ALTER COLUMN contact_email DROP NOT NULL;
ALTER TABLE user_profile ALTER COLUMN gender_type DROP NOT NULL;
ALTER TABLE user_profile ALTER COLUMN disability_type DROP NOT NULL;
ALTER TABLE user_profile ALTER COLUMN disability_severity DROP NOT NULL;
ALTER TABLE user_profile ALTER COLUMN disability_registered_yn DROP NOT NULL;
ALTER TABLE user_profile DROP CONSTRAINT IF EXISTS chk_user_profile_birth_date_required;
ALTER TABLE user_profile DROP CONSTRAINT IF EXISTS chk_user_profile_detail_address_required;

UPDATE user_profile
SET
    full_name = NULL,
    contact_phone = NULL,
    contact_email = NULL,
    birth_date = NULL,
    gender_type = NULL,
    detail_address = NULL,
    home_lat = NULL,
    home_lng = NULL,
    home_geocoded_address = NULL,
    emergency_contact = NULL,
    required_supports_json = '[]',
    disability_type = NULL,
    disability_severity = NULL,
    disability_registered_yn = NULL,
    sensitive_info_consent_yn = NULL,
    disability_description = NULL,
    assistive_devices = NULL,
    work_support_requirements = NULL;

ALTER TABLE admin_account
    ADD COLUMN IF NOT EXISTS sensitive_profile_access_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS personal_data_access_audit (
    id BIGSERIAL PRIMARY KEY,
    admin_user_id BIGINT NOT NULL,
    action_type VARCHAR(80) NOT NULL,
    target_user_id BIGINT,
    target_profile_id BIGINT,
    request_ip VARCHAR(120),
    access_outcome VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_personal_data_access_audit_admin_created_at
    ON personal_data_access_audit (admin_user_id, created_at DESC);
