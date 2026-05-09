package com.bridgework.recommend.controller;

import com.bridgework.auth.exception.UnauthorizedException;
import com.bridgework.auth.security.UserPrincipal;
import com.bridgework.recommend.dto.RecommendRequestDto;
import com.bridgework.recommend.dto.RecommendResponseDto;
import com.bridgework.recommend.service.RecommendGatewayService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recommend")
@Tag(name = "Recommend", description = "추천 게이트웨이 API")
@SecurityRequirement(name = "bearerAuth")
public class RecommendController {

    private final RecommendGatewayService recommendGatewayService;

    public RecommendController(RecommendGatewayService recommendGatewayService) {
        this.recommendGatewayService = recommendGatewayService;
    }

    @PostMapping("/quick")
    @Operation(
            summary = "기능2 퀵 맞춤 일자리 추천",
            description = "aiEnabled=true면 선택 프로필만 FastAPI로 전달하고 응답을 반환한다. aiEnabled=false면 Spring이 DB 공고를 최신순으로 반환한다."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = false,
            content = @Content(examples = {
                    @ExampleObject(name = "AI ON", value = "{\"aiEnabled\":true,\"profileId\":3}"),
                    @ExampleObject(name = "AI OFF", value = "{\"aiEnabled\":false}")
            })
    )
    @ApiResponse(responseCode = "200", description = "추천 결과",
            content = @Content(examples = {
                    @ExampleObject(
                            name = "AI ON 응답 예시",
                            value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"aiEnabled\":true,\"profileId\":3,\"jobs\":[{\"externalId\":\"KEPAD-20260508-0001\",\"busplaName\":\"브릿지웍스\",\"jobNm\":\"사무보조\",\"compAddr\":\"서울\",\"empType\":\"정규직\",\"enterType\":\"신입\",\"salaryType\":\"월급\",\"salary\":\"3200만원\",\"termDate\":\"20261231\",\"offerregDt\":\"20260508\",\"regDt\":\"20260508\",\"reqCareer\":\"무관\",\"reqEduc\":\"고졸\",\"reqMajor\":\"무관\",\"reqLicens\":\"무관\",\"geoLatitude\":37.498095,\"geoLongitude\":127.027610}],\"aiResponse\":{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"results\":[{\"job\":{\"external_id\":\"KEPAD-20260508-0001\",\"company_name\":\"브릿지웍스\",\"job_title\":\"사무보조\"},\"job_fit_score\":86}]}}}}"
                    ),
                    @ExampleObject(
                            name = "AI OFF 응답 예시",
                            value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"aiEnabled\":false,\"profileId\":null,\"jobs\":[{\"externalId\":\"KEPAD-20260508-0001\",\"busplaName\":\"브릿지웍스\",\"jobNm\":\"사무보조\",\"compAddr\":\"서울\",\"empType\":\"정규직\",\"enterType\":\"신입\",\"salaryType\":\"월급\",\"salary\":\"3200만원\",\"termDate\":\"20261231\",\"offerregDt\":\"20260508\",\"regDt\":\"20260508\",\"reqCareer\":\"무관\",\"reqEduc\":\"고졸\",\"reqMajor\":\"무관\",\"reqLicens\":\"무관\",\"geoLatitude\":37.498095,\"geoLongitude\":127.027610}],\"aiResponse\":null}}"
                    )
            }))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<RecommendResponseDto>> recommendQuick(
            Authentication authentication,
            @RequestBody(required = false) RecommendRequestDto request
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(recommendGatewayService.recommendQuick(userId, request)));
    }

    @PostMapping("/map")
    @Operation(
            summary = "기능3 지역 접근성 지도 추천",
            description = "aiEnabled=true면 선택 프로필만 FastAPI로 전달하고 응답을 반환한다. aiEnabled=false면 Spring이 DB 공고를 반환한다."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = false,
            content = @Content(examples = {
                    @ExampleObject(name = "AI ON", value = "{\"aiEnabled\":true,\"profileId\":3}"),
                    @ExampleObject(name = "AI OFF", value = "{\"aiEnabled\":false}")
            })
    )
    @ApiResponse(responseCode = "200", description = "추천 결과",
            content = @Content(examples = {
                    @ExampleObject(
                            name = "AI ON 응답 예시",
                            value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"aiEnabled\":true,\"profileId\":3,\"jobs\":[{\"externalId\":\"KEPAD-20260508-0001\",\"busplaName\":\"브릿지웍스\",\"jobNm\":\"사무보조\",\"compAddr\":\"서울\",\"empType\":\"정규직\",\"enterType\":\"신입\",\"salaryType\":\"월급\",\"salary\":\"3200만원\",\"termDate\":\"20261231\",\"offerregDt\":\"20260508\",\"regDt\":\"20260508\",\"reqCareer\":\"무관\",\"reqEduc\":\"고졸\",\"reqMajor\":\"무관\",\"reqLicens\":\"무관\",\"geoLatitude\":37.498095,\"geoLongitude\":127.027610}],\"aiResponse\":{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"results\":[{\"job\":{\"external_id\":\"KEPAD-20260508-0001\",\"company_name\":\"브릿지웍스\",\"job_title\":\"사무보조\"},\"total_score\":84,\"score_detail\":{\"job_fit_score\":86,\"work_condition_score\":80,\"disability_support_score\":82,\"work_environment_score\":85,\"company_stability_score\":83,\"accessibility_score\":88}}]}}}}"
                    ),
                    @ExampleObject(
                            name = "AI OFF 응답 예시",
                            value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"aiEnabled\":false,\"profileId\":null,\"jobs\":[{\"externalId\":\"KEPAD-20260508-0001\",\"busplaName\":\"브릿지웍스\",\"jobNm\":\"사무보조\",\"compAddr\":\"서울\",\"empType\":\"정규직\",\"enterType\":\"신입\",\"salaryType\":\"월급\",\"salary\":\"3200만원\",\"termDate\":\"20261231\",\"offerregDt\":\"20260508\",\"regDt\":\"20260508\",\"reqCareer\":\"무관\",\"reqEduc\":\"고졸\",\"reqMajor\":\"무관\",\"reqLicens\":\"무관\",\"geoLatitude\":37.498095,\"geoLongitude\":127.027610}],\"aiResponse\":null}}"
                    )
            }))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<RecommendResponseDto>> recommendMap(
            Authentication authentication,
            @RequestBody(required = false) RecommendRequestDto request
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(recommendGatewayService.recommendMap(userId, request)));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException();
        }
        return principal.getUserId();
    }
}
