-- FastAPI map/recommend bounding box 조회 성능 개선을 위한 좌표 인덱스
CREATE INDEX IF NOT EXISTS idx_pd_nationwide_bus_stop_lat_lng
    ON pd_nationwide_bus_stop (latitude, longitude);

CREATE INDEX IF NOT EXISTS idx_pd_nationwide_crosswalk_lat_lng
    ON pd_nationwide_crosswalk (latitude, longitude);

CREATE INDEX IF NOT EXISTS idx_pd_nationwide_traffic_light_lat_lng
    ON pd_nationwide_traffic_light (latitude, longitude);

CREATE INDEX IF NOT EXISTS idx_pd_transport_support_center_lat_lng
    ON pd_transport_support_center (latitude, longitude);
