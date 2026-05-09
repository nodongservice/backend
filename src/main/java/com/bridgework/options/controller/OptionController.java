package com.bridgework.options.controller;

import com.bridgework.options.dto.JobCategoryTreeNodeDto;
import com.bridgework.options.dto.OptionItemDto;
import com.bridgework.options.service.OptionQueryService;
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
@RequestMapping("/api/v1/options")
@Tag(name = "Option", description = "화면 필터 옵션 조회 API")
public class OptionController {

    private final OptionQueryService optionQueryService;

    public OptionController(OptionQueryService optionQueryService) {
        this.optionQueryService = optionQueryService;
    }

    @GetMapping("/job-categories/tree")
    @Operation(summary = "희망 직무 트리 조회", description = "동기화된 장애인 고용직무분류 데이터를 대분류/중분류/소분류 트리 구조로 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(
                    value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":[{\"code\":\"A\",\"name\":\"사무직\",\"level\":1},{\"code\":\"A01\",\"name\":\"총무\",\"level\":2},{\"code\":\"A0101\",\"name\":\"사무보조\",\"level\":3}]}"
            )))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<List<JobCategoryTreeNodeDto>>> getJobCategoryTree() {
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(optionQueryService.getJobCategoryTree()));
    }

    @GetMapping("/regions")
    @Operation(summary = "근무지역 옵션 조회", description = "전국 17개 시/도 근무지역 옵션을 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":[{\"value\":\"서울\",\"label\":\"서울\"},{\"value\":\"부산\",\"label\":\"부산\"}]}")))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<List<OptionItemDto>>> getRegions() {
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(optionQueryService.getRegions()));
    }

    @GetMapping("/employment-types")
    @Operation(summary = "고용형태 옵션 조회", description = "화면 필터에서 사용할 고용형태 옵션을 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":[{\"value\":\"정규직\",\"label\":\"정규직\"},{\"value\":\"재택/원격\",\"label\":\"재택/원격\"}]}")))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<List<OptionItemDto>>> getEmploymentTypes() {
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(optionQueryService.getEmploymentTypes()));
    }

    @GetMapping("/salary-types")
    @Operation(summary = "급여 방식 옵션 조회", description = "화면 필터에서 사용할 급여 방식 옵션을 조회한다.")
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(examples = @ExampleObject(value = "{\"code\":\"SUCCESS\",\"message\":\"요청이 성공했습니다.\",\"result\":[{\"value\":\"월급\",\"label\":\"월급\"},{\"value\":\"연봉\",\"label\":\"연봉\"}]}")))
    public ResponseEntity<com.bridgework.common.dto.ApiResponse<List<OptionItemDto>>> getSalaryTypes() {
        return ResponseEntity.ok(com.bridgework.common.dto.ApiResponse.success(optionQueryService.getSalaryTypes()));
    }
}
