CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(128) NOT NULL,
    email VARCHAR(255),
    name VARCHAR(100) NOT NULL,
    age INTEGER NOT NULL,
    gender VARCHAR(20) NOT NULL,
    location VARCHAR(200) NOT NULL,
    phone_number VARCHAR(32) NOT NULL,
    role VARCHAR(20) NOT NULL,
    signup_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_app_user_provider UNIQUE (provider, provider_user_id),
    CONSTRAINT uk_app_user_email UNIQUE (email),
    CONSTRAINT uk_app_user_phone_number UNIQUE (phone_number)
);

CREATE INDEX IF NOT EXISTS idx_app_user_created_at ON app_user (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_app_user_provider ON app_user (provider, provider_user_id);

CREATE TABLE IF NOT EXISTS onboarding_profile (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    desired_job VARCHAR(200) NOT NULL,
    commute_range VARCHAR(120) NOT NULL,
    preferred_work_environments_json TEXT NOT NULL,
    avoided_work_environments_json TEXT NOT NULL,
    required_supports_json TEXT NOT NULL,
    disability_type VARCHAR(120) NOT NULL,
    career_summary VARCHAR(500) NOT NULL,
    education_summary VARCHAR(500) NOT NULL,
    employment_type_summary VARCHAR(200) NOT NULL,

    full_name VARCHAR(100) NOT NULL,
    contact_phone VARCHAR(32) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    birth_date DATE,
    age_group VARCHAR(50),
    residence_region VARCHAR(120) NOT NULL,
    detail_address VARCHAR(300),
    emergency_contact VARCHAR(100),
    profile_image_url VARCHAR(500),

    highest_education VARCHAR(300) NOT NULL,
    graduation_status VARCHAR(80) NOT NULL,
    major_career VARCHAR(500) NOT NULL,
    career_detail TEXT,
    project_experience TEXT,
    career_gap_reason VARCHAR(500),

    target_job VARCHAR(200) NOT NULL,
    skills_json TEXT NOT NULL,
    certifications_json TEXT,
    portfolio_url VARCHAR(500),
    awards TEXT,
    trainings TEXT,

    disability_yn BOOLEAN NOT NULL,
    disability_severity VARCHAR(80) NOT NULL,
    disability_registered_yn BOOLEAN NOT NULL,
    disability_description TEXT,
    assistive_devices TEXT,
    work_support_requirements TEXT,

    work_availability VARCHAR(80) NOT NULL,
    work_types_json TEXT NOT NULL,
    expected_salary VARCHAR(120),
    work_time_preference VARCHAR(200),
    remote_available_yn BOOLEAN,
    mobility_range VARCHAR(200),

    self_introduction TEXT NOT NULL,
    motivation TEXT NOT NULL,
    job_fit_description TEXT,
    career_goal TEXT,
    strengths_weaknesses TEXT,

    military_service VARCHAR(120),
    patriotic_veteran_yn BOOLEAN,
    referrer VARCHAR(200),
    sns_url VARCHAR(500),

    ai_job_tags_json TEXT NOT NULL,
    ai_environment_tags_json TEXT NOT NULL,
    ai_support_tags_json TEXT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_onboarding_profile_user
        FOREIGN KEY (user_id)
        REFERENCES app_user (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_onboarding_profile_user UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_onboarding_profile_updated_at ON onboarding_profile (updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_onboarding_profile_user_id ON onboarding_profile (user_id);
