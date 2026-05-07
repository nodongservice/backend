CREATE TABLE IF NOT EXISTS admin_account (
    id BIGSERIAL PRIMARY KEY,
    login_id VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ADMIN',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_admin_account_login_id UNIQUE (login_id),
    CONSTRAINT ck_admin_account_role CHECK (role = 'ADMIN'),
    CONSTRAINT ck_admin_account_login_id_lower CHECK (login_id = lower(login_id))
);

CREATE INDEX IF NOT EXISTS idx_admin_account_active ON admin_account (is_active);
CREATE INDEX IF NOT EXISTS idx_admin_account_locked_until ON admin_account (locked_until);
