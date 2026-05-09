ALTER TABLE pd_transport_support_center
    ALTER COLUMN latitude TYPE DOUBLE PRECISION
        USING (
            CASE
                WHEN latitude IS NULL THEN NULL
                WHEN btrim(latitude) = '' THEN NULL
                WHEN btrim(latitude) ~ '^[+-]?([0-9]+(\\.[0-9]+)?|\\.[0-9]+)$' THEN btrim(latitude)::DOUBLE PRECISION
                ELSE NULL
            END
        ),
    ALTER COLUMN longitude TYPE DOUBLE PRECISION
        USING (
            CASE
                WHEN longitude IS NULL THEN NULL
                WHEN btrim(longitude) = '' THEN NULL
                WHEN btrim(longitude) ~ '^[+-]?([0-9]+(\\.[0-9]+)?|\\.[0-9]+)$' THEN btrim(longitude)::DOUBLE PRECISION
                ELSE NULL
            END
        );

ALTER TABLE pd_nationwide_bus_stop
    ALTER COLUMN latitude TYPE DOUBLE PRECISION
        USING (
            CASE
                WHEN latitude IS NULL THEN NULL
                WHEN btrim(latitude) = '' THEN NULL
                WHEN btrim(latitude) ~ '^[+-]?([0-9]+(\\.[0-9]+)?|\\.[0-9]+)$' THEN btrim(latitude)::DOUBLE PRECISION
                ELSE NULL
            END
        ),
    ALTER COLUMN longitude TYPE DOUBLE PRECISION
        USING (
            CASE
                WHEN longitude IS NULL THEN NULL
                WHEN btrim(longitude) = '' THEN NULL
                WHEN btrim(longitude) ~ '^[+-]?([0-9]+(\\.[0-9]+)?|\\.[0-9]+)$' THEN btrim(longitude)::DOUBLE PRECISION
                ELSE NULL
            END
        );

ALTER TABLE pd_nationwide_traffic_light
    ALTER COLUMN latitude TYPE DOUBLE PRECISION
        USING (
            CASE
                WHEN latitude IS NULL THEN NULL
                WHEN btrim(latitude) = '' THEN NULL
                WHEN btrim(latitude) ~ '^[+-]?([0-9]+(\\.[0-9]+)?|\\.[0-9]+)$' THEN btrim(latitude)::DOUBLE PRECISION
                ELSE NULL
            END
        ),
    ALTER COLUMN longitude TYPE DOUBLE PRECISION
        USING (
            CASE
                WHEN longitude IS NULL THEN NULL
                WHEN btrim(longitude) = '' THEN NULL
                WHEN btrim(longitude) ~ '^[+-]?([0-9]+(\\.[0-9]+)?|\\.[0-9]+)$' THEN btrim(longitude)::DOUBLE PRECISION
                ELSE NULL
            END
        );

ALTER TABLE pd_nationwide_crosswalk
    ALTER COLUMN latitude TYPE DOUBLE PRECISION
        USING (
            CASE
                WHEN latitude IS NULL THEN NULL
                WHEN btrim(latitude) = '' THEN NULL
                WHEN btrim(latitude) ~ '^[+-]?([0-9]+(\\.[0-9]+)?|\\.[0-9]+)$' THEN btrim(latitude)::DOUBLE PRECISION
                ELSE NULL
            END
        ),
    ALTER COLUMN longitude TYPE DOUBLE PRECISION
        USING (
            CASE
                WHEN longitude IS NULL THEN NULL
                WHEN btrim(longitude) = '' THEN NULL
                WHEN btrim(longitude) ~ '^[+-]?([0-9]+(\\.[0-9]+)?|\\.[0-9]+)$' THEN btrim(longitude)::DOUBLE PRECISION
                ELSE NULL
            END
        );

ALTER TABLE pd_transport_support_center
    ADD CONSTRAINT chk_pd_transport_support_center_latitude_range
        CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)) NOT VALID,
    ADD CONSTRAINT chk_pd_transport_support_center_longitude_range
        CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180)) NOT VALID;

ALTER TABLE pd_nationwide_bus_stop
    ADD CONSTRAINT chk_pd_nationwide_bus_stop_latitude_range
        CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)) NOT VALID,
    ADD CONSTRAINT chk_pd_nationwide_bus_stop_longitude_range
        CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180)) NOT VALID;

ALTER TABLE pd_nationwide_traffic_light
    ADD CONSTRAINT chk_pd_nationwide_traffic_light_latitude_range
        CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)) NOT VALID,
    ADD CONSTRAINT chk_pd_nationwide_traffic_light_longitude_range
        CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180)) NOT VALID;

ALTER TABLE pd_nationwide_crosswalk
    ADD CONSTRAINT chk_pd_nationwide_crosswalk_latitude_range
        CHECK (latitude IS NULL OR (latitude >= -90 AND latitude <= 90)) NOT VALID,
    ADD CONSTRAINT chk_pd_nationwide_crosswalk_longitude_range
        CHECK (longitude IS NULL OR (longitude >= -180 AND longitude <= 180)) NOT VALID;
