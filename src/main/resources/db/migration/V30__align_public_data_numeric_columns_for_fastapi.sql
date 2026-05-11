ALTER TABLE pd_transport_support_center
    ALTER COLUMN car_hold_co TYPE INTEGER
        USING (
            CASE
                WHEN car_hold_co IS NULL THEN NULL
                WHEN btrim(car_hold_co::TEXT) = '' THEN NULL
                WHEN btrim(car_hold_co::TEXT) ~ '^[+-]?[0-9]+$' THEN btrim(car_hold_co::TEXT)::INTEGER
                ELSE NULL
            END
        );

ALTER TABLE pd_korail_week_person_facilities
    ALTER COLUMN whlch_liftt_cnt TYPE INTEGER
        USING (
            CASE
                WHEN whlch_liftt_cnt IS NULL THEN NULL
                WHEN btrim(whlch_liftt_cnt::TEXT) = '' THEN NULL
                WHEN btrim(whlch_liftt_cnt::TEXT) ~ '^[+-]?[0-9]+$' THEN btrim(whlch_liftt_cnt::TEXT)::INTEGER
                ELSE NULL
            END
        );

ALTER TABLE pd_seoul_low_floor_bus_route_retention
    ALTER COLUMN authorized_count TYPE INTEGER
        USING (
            CASE
                WHEN authorized_count IS NULL THEN NULL
                WHEN btrim(authorized_count::TEXT) = '' THEN NULL
                WHEN btrim(authorized_count::TEXT) ~ '^[+-]?[0-9]+$' THEN btrim(authorized_count::TEXT)::INTEGER
                ELSE NULL
            END
        ),
    ALTER COLUMN low_floor_bus_count TYPE INTEGER
        USING (
            CASE
                WHEN low_floor_bus_count IS NULL THEN NULL
                WHEN btrim(low_floor_bus_count::TEXT) = '' THEN NULL
                WHEN btrim(low_floor_bus_count::TEXT) ~ '^[+-]?[0-9]+$' THEN btrim(low_floor_bus_count::TEXT)::INTEGER
                ELSE NULL
            END
        );

ALTER TABLE pd_nationwide_crosswalk
    ALTER COLUMN cartrk_co TYPE INTEGER
        USING (
            CASE
                WHEN cartrk_co IS NULL THEN NULL
                WHEN btrim(cartrk_co::TEXT) = '' THEN NULL
                WHEN btrim(cartrk_co::TEXT) ~ '^[+-]?[0-9]+$' THEN btrim(cartrk_co::TEXT)::INTEGER
                ELSE NULL
            END
        );

ALTER TABLE pd_transport_support_center
    ADD CONSTRAINT chk_pd_transport_support_center_car_hold_co_non_negative
        CHECK (car_hold_co IS NULL OR car_hold_co >= 0) NOT VALID;

ALTER TABLE pd_korail_week_person_facilities
    ADD CONSTRAINT chk_pd_korail_week_person_facilities_whlch_liftt_cnt_non_negative
        CHECK (whlch_liftt_cnt IS NULL OR whlch_liftt_cnt >= 0) NOT VALID;

ALTER TABLE pd_seoul_low_floor_bus_route_retention
    ADD CONSTRAINT chk_pd_seoul_low_floor_bus_route_retention_authorized_count_non_negative
        CHECK (authorized_count IS NULL OR authorized_count >= 0) NOT VALID,
    ADD CONSTRAINT chk_pd_seoul_low_floor_bus_route_retention_low_floor_bus_count_non_negative
        CHECK (low_floor_bus_count IS NULL OR low_floor_bus_count >= 0) NOT VALID;

ALTER TABLE pd_nationwide_crosswalk
    ADD CONSTRAINT chk_pd_nationwide_crosswalk_cartrk_co_non_negative
        CHECK (cartrk_co IS NULL OR cartrk_co >= 0) NOT VALID;
