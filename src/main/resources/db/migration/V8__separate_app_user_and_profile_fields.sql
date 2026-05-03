ALTER TABLE app_user
    DROP CONSTRAINT IF EXISTS uk_app_user_phone_number;

ALTER TABLE app_user
    DROP COLUMN IF EXISTS name,
    DROP COLUMN IF EXISTS age,
    DROP COLUMN IF EXISTS gender,
    DROP COLUMN IF EXISTS location,
    DROP COLUMN IF EXISTS phone_number;
