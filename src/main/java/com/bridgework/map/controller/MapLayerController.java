package com.bridgework.map.controller;

import com.bridgework.map.dto.SupportAgencyMarkerDto;
import com.bridgework.map.service.MapLayerQueryService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/map")
@Tag(name = "Map", description = "지도 레이어 조회 API")
public class MapLayerController {

    private final MapLayerQueryService mapLayerQueryService;

    public MapLayerController(MapLayerQueryService mapLayerQueryService) {
        this.mapLayerQueryService = mapLayerQueryService;
    }

    @GetMapping("/support-agencies")
    @Operation(summary = "근로지원인 수행기관 마커 조회", description = "지오코딩된 근로지원인 수행기관 데이터를 지도 마커 용도로 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(
                    value = "[{\"externalId\":\"S001\",\"institutionCode\":\"IC001\",\"institutionName\":\"서울장애인근로지원센터\",\"address\":\"서울특별시 중구 세종대로\",\"telephone\":\"02-1234-5678\",\"latitude\":37.5665,\"longitude\":126.9780}]"
            )))
    public ResponseEntity<List<SupportAgencyMarkerDto>> getSupportAgencies() {
        return ResponseEntity.ok(mapLayerQueryService.getSupportAgencies());
    }
}
