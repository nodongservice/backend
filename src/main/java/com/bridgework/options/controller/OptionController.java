package com.bridgework.options.controller;

import com.bridgework.options.dto.JobCategoryTreeNodeDto;
import com.bridgework.options.dto.OptionItemDto;
import com.bridgework.options.service.OptionQueryService;
import io.swagger.v3.oas.annotations.Operation;
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
    public ResponseEntity<List<JobCategoryTreeNodeDto>> getJobCategoryTree() {
        return ResponseEntity.ok(optionQueryService.getJobCategoryTree());
    }

    @GetMapping("/regions")
    @Operation(summary = "근무지역 옵션 조회", description = "전국 17개 시/도 근무지역 옵션을 조회한다.")
    public ResponseEntity<List<OptionItemDto>> getRegions() {
        return ResponseEntity.ok(optionQueryService.getRegions());
    }

    @GetMapping("/employment-types")
    @Operation(summary = "고용형태 옵션 조회", description = "화면 필터에서 사용할 고용형태 옵션을 조회한다.")
    public ResponseEntity<List<OptionItemDto>> getEmploymentTypes() {
        return ResponseEntity.ok(optionQueryService.getEmploymentTypes());
    }

    @GetMapping("/salary-types")
    @Operation(summary = "급여 방식 옵션 조회", description = "화면 필터에서 사용할 급여 방식 옵션을 조회한다.")
    public ResponseEntity<List<OptionItemDto>> getSalaryTypes() {
        return ResponseEntity.ok(optionQueryService.getSalaryTypes());
    }
}
