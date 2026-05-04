package com.bridgework.map.service;

import com.bridgework.map.dto.SupportAgencyMarkerDto;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MapLayerQueryService {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public MapLayerQueryService(NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    public List<SupportAgencyMarkerDto> getSupportAgencies() {
        String sql = """
                SELECT external_id,
                       exc_instn,
                       exc_instn_nm,
                       COALESCE(geo_matched_address, exc_instn_addr) AS resolved_address,
                       exc_instn_telno,
                       geo_latitude,
                       geo_longitude
                FROM pd_kepad_support_agency
                WHERE geo_latitude IS NOT NULL
                  AND geo_longitude IS NOT NULL
                ORDER BY updated_at DESC, external_id ASC
                """;

        return namedParameterJdbcTemplate.query(sql, (rs, rowNum) -> new SupportAgencyMarkerDto(
                rs.getString("external_id"),
                rs.getString("exc_instn"),
                rs.getString("exc_instn_nm"),
                rs.getString("resolved_address"),
                rs.getString("exc_instn_telno"),
                rs.getObject("geo_latitude", Double.class),
                rs.getObject("geo_longitude", Double.class)
        ));
    }
}
