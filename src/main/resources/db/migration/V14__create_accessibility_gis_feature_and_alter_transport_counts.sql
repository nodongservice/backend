ALTER TABLE pd_transport_support_center
    ALTER COLUMN slope_vhcle_co TYPE INTEGER
        USING (
            CASE
                WHEN slope_vhcle_co IS NULL THEN NULL
                WHEN btrim(slope_vhcle_co) = '' THEN NULL
                WHEN btrim(slope_vhcle_co) ~ '^[+-]?[0-9]+$' THEN btrim(slope_vhcle_co)::INTEGER
                ELSE NULL
            END
        ),
    ALTER COLUMN lift_vhcle_co TYPE INTEGER
        USING (
            CASE
                WHEN lift_vhcle_co IS NULL THEN NULL
                WHEN btrim(lift_vhcle_co) = '' THEN NULL
                WHEN btrim(lift_vhcle_co) ~ '^[+-]?[0-9]+$' THEN btrim(lift_vhcle_co)::INTEGER
                ELSE NULL
            END
        );

ALTER TABLE pd_transport_support_center
    ADD CONSTRAINT chk_pd_transport_support_center_slope_vhcle_co_non_negative
        CHECK (slope_vhcle_co IS NULL OR slope_vhcle_co >= 0) NOT VALID,
    ADD CONSTRAINT chk_pd_transport_support_center_lift_vhcle_co_non_negative
        CHECK (lift_vhcle_co IS NULL OR lift_vhcle_co >= 0) NOT VALID;

CREATE TABLE IF NOT EXISTS public_accessibility_gis_feature (
    id BIGSERIAL PRIMARY KEY,
    public_data_record_id BIGINT NOT NULL,
    source_type VARCHAR(100) NOT NULL,
    feature_type VARCHAR(100) NOT NULL,
    name VARCHAR(255),
    address TEXT,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    geom geometry(GEOMETRY, 4326),
    geog geography(GEOMETRY, 4326),
    properties JSONB,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_public_accessibility_gis_feature_record_feature UNIQUE (public_data_record_id, feature_type),
    CONSTRAINT fk_public_accessibility_gis_feature_record
        FOREIGN KEY (public_data_record_id)
        REFERENCES public_data_record (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_public_accessibility_gis_feature_record_id
    ON public_accessibility_gis_feature (public_data_record_id);
CREATE INDEX IF NOT EXISTS idx_public_accessibility_gis_feature_source_type
    ON public_accessibility_gis_feature (source_type);
CREATE INDEX IF NOT EXISTS idx_public_accessibility_gis_feature_feature_type
    ON public_accessibility_gis_feature (feature_type);
CREATE INDEX IF NOT EXISTS idx_public_accessibility_gis_feature_is_active
    ON public_accessibility_gis_feature (is_active);
CREATE INDEX IF NOT EXISTS idx_public_accessibility_gis_feature_lat_lng
    ON public_accessibility_gis_feature (latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_public_accessibility_gis_feature_geog_gist
    ON public_accessibility_gis_feature
    USING GIST (geog);
