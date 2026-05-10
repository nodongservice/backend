package com.bridgework.posting.controller;

import com.bridgework.auth.exception.UnauthorizedException;
import com.bridgework.auth.security.UserPrincipal;
import com.bridgework.common.dto.ApiResponse;
import com.bridgework.posting.dto.PostingDetailDto;
import com.bridgework.posting.dto.PostingListItemDto;
import com.bridgework.posting.dto.ScrapCommandResponseDto;
import com.bridgework.posting.service.PostingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Posting", description = "공고/스크랩 API")
public class PostingController {

    private final PostingService postingService;

    public PostingController(PostingService postingService) {
        this.postingService = postingService;
    }

    @GetMapping("/postings/popular")
    @Operation(summary = "인기 공고 TOP N 조회", description = "스크랩 수 기준으로 ACTIVE 공고를 내림차순 조회한다.")
    public ResponseEntity<ApiResponse<List<PostingListItemDto>>> getPopularPostings(
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(postingService.getPopularPostings(limit)));
    }

    @GetMapping("/postings/{postingId}")
    @Operation(summary = "공고 상세 조회", description = "공고 상세와 전체 스크랩 수를 조회한다.")
    public ResponseEntity<ApiResponse<PostingDetailDto>> getPostingDetail(
            @PathVariable Long postingId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(postingService.getPostingDetail(postingId, currentUserId(authentication))));
    }

    @PostMapping("/postings/{postingId}/scraps")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "공고 스크랩", description = "이미 스크랩한 공고여도 멱등하게 성공 응답을 반환한다.")
    public ResponseEntity<ApiResponse<ScrapCommandResponseDto>> scrapPosting(
            @PathVariable Long postingId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(postingService.scrapPosting(currentUserId(authentication), postingId)));
    }

    @DeleteMapping("/postings/{postingId}/scraps")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "공고 스크랩 삭제", description = "스크랩이 없는 경우에도 멱등하게 성공 응답을 반환한다.")
    public ResponseEntity<ApiResponse<ScrapCommandResponseDto>> deleteScrap(
            @PathVariable Long postingId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.success(postingService.deleteScrap(currentUserId(authentication), postingId)));
    }

    @GetMapping("/me/scraps")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "내 스크랩 공고 목록 조회", description = "사용자가 스크랩한 공고를 최신 스크랩 순으로 조회한다.")
    public ResponseEntity<ApiResponse<List<PostingListItemDto>>> getMyScraps(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(postingService.getMyScrappedPostings(currentUserId(authentication))));
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException();
        }
        return principal.getUserId();
    }

}
