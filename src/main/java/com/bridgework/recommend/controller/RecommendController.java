package com.bridgework.recommend.controller;

import com.bridgework.auth.exception.UnauthorizedException;
import com.bridgework.auth.security.UserPrincipal;
import com.bridgework.recommend.dto.RecommendExplainRequestDto;
import com.bridgework.recommend.dto.RecommendExplainResponseDto;
import com.bridgework.recommend.dto.RecommendRequestDto;
import com.bridgework.recommend.service.RecommendGatewayService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
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
                            value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"aiEnabled\":true,\"profileId\":3,\"jobs\":[{\"jobPostId\":12345,\"sourceId\":12345,\"sourceTable\":\"pd_kepad_recruitment\",\"busplaName\":\"브릿지웍스\",\"jobNm\":\"사무보조\",\"compAddr\":\"서울\",\"empType\":\"정규직\",\"enterType\":\"신입\",\"salaryType\":\"월급\",\"salary\":\"3200만원\",\"termDate\":\"20261231\",\"offerregDt\":\"20260508\",\"regDt\":\"20260508\",\"reqCareer\":\"무관\",\"reqEduc\":\"고졸\",\"reqMajor\":\"무관\",\"reqLicens\":\"무관\",\"regagnName\":\"서울강남고용센터\",\"geoLatitude\":37.498095,\"geoLongitude\":127.027610}],\"aiResponse\":{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"results\":[{\"job\":{\"job_post_id\":12345,\"company_name\":\"브릿지웍스\",\"job_title\":\"사무보조\"},\"job_fit_score\":86}]}}}}"
                    ),
                    @ExampleObject(
                            name = "AI OFF 응답 예시",
                            value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"aiEnabled\":false,\"profileId\":null,\"jobs\":[{\"jobPostId\":12345,\"sourceId\":12345,\"sourceTable\":\"pd_kepad_recruitment\",\"busplaName\":\"브릿지웍스\",\"jobNm\":\"사무보조\",\"compAddr\":\"서울\",\"empType\":\"정규직\",\"enterType\":\"신입\",\"salaryType\":\"월급\",\"salary\":\"3200만원\",\"termDate\":\"20261231\",\"offerregDt\":\"20260508\",\"regDt\":\"20260508\",\"reqCareer\":\"무관\",\"reqEduc\":\"고졸\",\"reqMajor\":\"무관\",\"reqLicens\":\"무관\",\"regagnName\":\"서울강남고용센터\",\"geoLatitude\":37.498095,\"geoLongitude\":127.027610}],\"aiResponse\":null}}"
                    )
            }))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<Map<String, Object>>> recommendQuick(
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
                            value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"aiEnabled\":true,\"profileId\":3,\"jobs\":[{\"jobPostId\":12345,\"sourceId\":12345,\"sourceTable\":\"pd_kepad_recruitment\",\"busplaName\":\"브릿지웍스\",\"jobNm\":\"사무보조\",\"compAddr\":\"서울\",\"empType\":\"정규직\",\"enterType\":\"신입\",\"salaryType\":\"월급\",\"salary\":\"3200만원\",\"termDate\":\"20261231\",\"offerregDt\":\"20260508\",\"regDt\":\"20260508\",\"reqCareer\":\"무관\",\"reqEduc\":\"고졸\",\"reqMajor\":\"무관\",\"reqLicens\":\"무관\",\"regagnName\":\"서울강남고용센터\",\"geoLatitude\":37.498095,\"geoLongitude\":127.027610}],\"aiResponse\":{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"results\":[{\"job\":{\"job_post_id\":12345,\"company_name\":\"브릿지웍스\",\"job_title\":\"사무보조\"},\"total_score\":84,\"score_detail\":{\"job_fit_score\":86,\"work_condition_score\":80,\"disability_support_score\":82,\"work_environment_score\":85,\"company_stability_score\":83,\"accessibility_score\":88}}]}}}}"
                    ),
                    @ExampleObject(
                            name = "AI OFF 응답 예시",
                            value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"aiEnabled\":false,\"profileId\":null,\"jobs\":[{\"jobPostId\":12345,\"sourceId\":12345,\"sourceTable\":\"pd_kepad_recruitment\",\"busplaName\":\"브릿지웍스\",\"jobNm\":\"사무보조\",\"compAddr\":\"서울\",\"empType\":\"정규직\",\"enterType\":\"신입\",\"salaryType\":\"월급\",\"salary\":\"3200만원\",\"termDate\":\"20261231\",\"offerregDt\":\"20260508\",\"regDt\":\"20260508\",\"reqCareer\":\"무관\",\"reqEduc\":\"고졸\",\"reqMajor\":\"무관\",\"reqLicens\":\"무관\",\"regagnName\":\"서울강남고용센터\",\"geoLatitude\":37.498095,\"geoLongitude\":127.027610}],\"aiResponse\":null}}"
                    )
            }))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<Map<String, Object>>> recommendMap(
            Authentication authentication,
            @RequestBody(required = false) RecommendRequestDto request
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(recommendGatewayService.recommendMap(userId, request)));
    }

    @PostMapping("/explain")
    @Operation(
            summary = "추천 설명 생성",
            description = "선택 프로필과 공고/점수 정보를 FastAPI 설명 생성 API로 전달해 추천 사유, 주의사항, 체크리스트를 반환한다."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(examples = {
                    @ExampleObject(
                            name = "퀵 추천 설명 생성",
                            value = "{\"profile\":{\"profileId\":3,\"userId\":1001,\"name\":\"홍길동\",\"address\":\"서울특별시 강남구\",\"desiredJobs\":[\"사무보조\"],\"skills\":[\"문서작성\"],\"education\":\"고졸\",\"career\":\"무관\",\"licenses\":[]},\"job\":{\"jobPostId\":12345,\"companyName\":\"브릿지웍스\",\"jobTitle\":\"사무보조\",\"workAddress\":\"서울특별시 강남구 테헤란로 123\",\"workLat\":37.498095,\"workLng\":127.027610,\"employmentType\":\"정규직\",\"enterType\":\"신입\",\"salaryType\":\"월급\",\"salary\":\"3200만원\",\"termDate\":\"20261231\",\"requiredCareer\":\"무관\",\"requiredEducation\":\"고졸\",\"requiredMajor\":\"무관\",\"requiredLicenses\":\"컴퓨터활용능력\",\"registeredAt\":\"20260508\",\"sourceTable\":\"pd_kepad_recruitment\",\"sourceId\":98765},\"jobFitScore\":86,\"reasons\":[\"지원 직무와 요구 역량이 유사합니다.\"],\"riskFactors\":[\"출퇴근 시간대 혼잡 가능성\"],\"evidenceItems\":[{\"sourceType\":\"BUS_STOP\",\"sourceName\":\"역삼역 버스정류장\",\"description\":\"근무지에서 320m 거리\",\"distanceMeters\":320.5,\"sourceTable\":\"pd_nationwide_bus_stop\",\"recordId\":123}]}"
                    ),
                    @ExampleObject(
                            name = "지도 추천 설명 생성",
                            value = "{\"profile\":{\"profile_id\":3,\"user_id\":1001,\"name\":\"홍길동\",\"address\":\"서울특별시 강남구\",\"desired_jobs\":[\"사무보조\"],\"skills\":[\"문서작성\"]},\"job\":{\"job_post_id\":12345,\"company_name\":\"브릿지웍스\",\"job_title\":\"사무보조\",\"work_address\":\"서울특별시 강남구 테헤란로 123\",\"employment_type\":\"정규직\",\"enter_type\":\"신입\",\"salary_type\":\"월급\",\"salary\":\"3200만원\",\"term_date\":\"20261231\",\"required_career\":\"무관\",\"required_education\":\"고졸\",\"required_major\":\"무관\",\"required_licenses\":\"컴퓨터활용능력\",\"registered_at\":\"20260508\",\"source_table\":\"pd_kepad_recruitment\",\"source_id\":12345},\"scoreDetail\":{\"jobFitScore\":86,\"workConditionScore\":80,\"disabilitySupportScore\":82,\"workEnvironmentScore\":85,\"companyStabilityScore\":83,\"accessibilityScore\":88},\"totalScore\":84,\"reasons\":[\"직무 적합도와 접근성이 균형적입니다.\"],\"riskFactors\":[\"출퇴근 시간 혼잡 가능성\"]}"
                    )
            })
    )
    @ApiResponse(responseCode = "200", description = "설명 생성 결과",
            content = @Content(examples = {
                    @ExampleObject(
                            name = "성공 응답 예시",
                            value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"profileId\":3,\"shortSummary\":\"직무 적합도와 접근성이 균형 잡힌 공고입니다.\",\"recommendationReasons\":[\"지원 직무와 요구 역량이 유사합니다.\"],\"cautionPoints\":[\"출퇴근 시간대 혼잡 가능성을 확인하세요.\"],\"checklist\":[\"면접 전 근무지 접근 경로를 확인하세요.\"],\"usedLlm\":false,\"aiResponse\":{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":{\"short_summary\":\"직무 적합도와 접근성이 균형 잡힌 공고입니다.\",\"recommendation_reasons\":[\"지원 직무와 요구 역량이 유사합니다.\"],\"caution_points\":[\"출퇴근 시간대 혼잡 가능성을 확인하세요.\"],\"checklist\":[\"면접 전 근무지 접근 경로를 확인하세요.\"],\"used_llm\":false}}}}"
                    )
            }))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<RecommendExplainResponseDto>> explainRecommendation(
            Authentication authentication,
            @Valid @RequestBody RecommendExplainRequestDto request
    ) {
        Long userId = currentUserId(authentication);
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(
                recommendGatewayService.explainRecommendation(userId, request)
        ));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException();
        }
        return principal.getUserId();
    }
}
