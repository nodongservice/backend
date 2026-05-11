ALTER TABLE user_profile
    ADD COLUMN home_lat DOUBLE PRECISION,
    ADD COLUMN home_lng DOUBLE PRECISION,
    ADD COLUMN home_geocoded_address VARCHAR(300);

ALTER TABLE user_profile
    ADD CONSTRAINT chk_user_profile_home_lat_range
        CHECK (home_lat IS NULL OR (home_lat >= -90 AND home_lat <= 90)) NOT VALID,
    ADD CONSTRAINT chk_user_profile_home_lng_range
        CHECK (home_lng IS NULL OR (home_lng >= -180 AND home_lng <= 180)) NOT VALID;
