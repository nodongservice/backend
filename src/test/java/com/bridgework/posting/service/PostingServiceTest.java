package com.bridgework.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bridgework.auth.entity.AppUser;
import com.bridgework.auth.repository.AppUserRepository;
import com.bridgework.common.notification.DiscordNotifierService;
import com.bridgework.posting.dto.PostingFeedbackCreateRequestDto;
import com.bridgework.posting.dto.PostingFeedbackCreateResponseDto;
import com.bridgework.posting.entity.JobScrap;
import com.bridgework.posting.entity.PostingFeedback;
import com.bridgework.posting.entity.PostingFeedbackReaction;
import com.bridgework.posting.repository.JobScrapRepository;
import com.bridgework.posting.repository.PostingFeedbackRepository;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PostingServiceTest {

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock
    private JobScrapRepository jobScrapRepository;
    @Mock
    private PostingFeedbackRepository postingFeedbackRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private DiscordNotifierService discordNotifierService;

    private PostingService postingService;

    @BeforeEach
    void setUp() {
        postingService = new PostingService(
                namedParameterJdbcTemplate,
                jobScrapRepository,
                postingFeedbackRepository,
                appUserRepository,
                discordNotifierService
        );
    }

    @Test
    void createPostingFeedback_whenDislike_thenSavesAndSendsDiscordNotification() throws Exception {
        mockPostingLookup(101L, "ext-101", "브릿지워크", "백엔드 개발자");
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(buildUser("user@example.com")));
        when(postingFeedbackRepository.save(any(PostingFeedback.class))).thenAnswer(invocation -> {
            PostingFeedback feedback = invocation.getArgument(0, PostingFeedback.class);
            ReflectionTestUtils.setField(feedback, "id", 55L);
            ReflectionTestUtils.setField(feedback, "createdAt", OffsetDateTime.parse("2026-07-10T02:30:00Z"));
            ReflectionTestUtils.setField(feedback, "updatedAt", OffsetDateTime.parse("2026-07-10T02:30:00Z"));
            return feedback;
        });

        PostingFeedbackCreateResponseDto response = postingService.createPostingFeedback(
                7L,
                101L,
                new PostingFeedbackCreateRequestDto(PostingFeedbackReaction.DISLIKE, "설명이 추상적입니다.")
        );

        assertThat(response.feedbackId()).isEqualTo(55L);
        assertThat(response.postingId()).isEqualTo(101L);
        assertThat(response.reaction()).isEqualTo(PostingFeedbackReaction.DISLIKE);
        verify(postingFeedbackRepository).save(any(PostingFeedback.class));
        verify(discordNotifierService).notifyAccessibilityFeedbackDisliked(
                eq(101L),
                eq("ext-101"),
                eq("브릿지워크"),
                eq("백엔드 개발자"),
                eq(7L),
                eq("user@example.com"),
                eq("설명이 추상적입니다."),
                eq(OffsetDateTime.parse("2026-07-10T02:30:00Z"))
        );
    }

    @Test
    void createPostingFeedback_whenLike_thenSavesWithoutDiscordNotification() throws Exception {
        mockPostingLookup(102L, "ext-102", "브릿지워크", "프론트엔드 개발자");
        when(postingFeedbackRepository.save(any(PostingFeedback.class))).thenAnswer(invocation -> {
            PostingFeedback feedback = invocation.getArgument(0, PostingFeedback.class);
            ReflectionTestUtils.setField(feedback, "id", 56L);
            ReflectionTestUtils.setField(feedback, "createdAt", OffsetDateTime.parse("2026-07-10T03:00:00Z"));
            ReflectionTestUtils.setField(feedback, "updatedAt", OffsetDateTime.parse("2026-07-10T03:00:00Z"));
            return feedback;
        });

        PostingFeedbackCreateResponseDto response = postingService.createPostingFeedback(
                8L,
                102L,
                new PostingFeedbackCreateRequestDto(PostingFeedbackReaction.LIKE, "설명이 명확해서 좋았습니다.")
        );

        assertThat(response.feedbackId()).isEqualTo(56L);
        assertThat(response.reaction()).isEqualTo(PostingFeedbackReaction.LIKE);
        verify(postingFeedbackRepository).save(any(PostingFeedback.class));
        verify(discordNotifierService, never()).notifyAccessibilityFeedbackDisliked(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private void mockPostingLookup(Long postingId, String externalId, String companyName, String jobTitle) throws Exception {
        when(namedParameterJdbcTemplate.query(any(String.class), any(SqlParameterSource.class), any(ResultSetExtractor.class))).thenAnswer(invocation -> {
            ResultSetExtractor<Object> extractor = invocation.getArgument(2, ResultSetExtractor.class);
            ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getLong("id")).thenReturn(postingId);
            when(resultSet.getString("external_id")).thenReturn(externalId);
            when(resultSet.getString("buspla_name")).thenReturn(companyName);
            when(resultSet.getString("job_nm")).thenReturn(jobTitle);
            return extractor.extractData(resultSet);
        });
    }

    private AppUser buildUser(String email) {
        AppUser user = new AppUser();
        user.setEmail(email);
        return user;
    }
}
