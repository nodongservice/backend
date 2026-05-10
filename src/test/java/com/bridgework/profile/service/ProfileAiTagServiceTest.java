package com.bridgework.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.bridgework.auth.entity.GenderType;
import com.bridgework.profile.dto.UserProfileUpsertRequestDto;
import com.bridgework.profile.enums.ProfileDisabilitySeverity;
import com.bridgework.profile.enums.ProfileDisabilityType;
import com.bridgework.profile.enums.ProfileGraduationStatus;
import com.bridgework.profile.enums.ProfileHighestEducation;
import com.bridgework.profile.enums.ProfileWorkAvailability;
import com.bridgework.profile.enums.ProfileWorkType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileAiTagServiceTest {

    private final ProfileAiTagService profileAiTagService = new ProfileAiTagService();

    @Test
    void buildTags_whenOptionalSupportFieldsAreNull_thenDoesNotThrowAndBuildsTags() {
        UserProfileUpsertRequestDto request = baseRequestWithNullableSupportFields();

        assertThatCode(() -> profileAiTagService.buildTags(request))
                .doesNotThrowAnyException();

        ProfileAiTags tags = profileAiTagService.buildTags(request);

        assertThat(tags.jobTags()).contains("사무보조", "엑셀");
        assertThat(tags.environmentTags()).contains("IMMEDIATE", "SEVERE");
        assertThat(tags.supportTags()).contains("PHYSICAL");
    }

    private UserProfileUpsertRequestDto baseRequestWithNullableSupportFields() {
        return new UserProfileUpsertRequestDto(
                "사무보조",
                "30분",
                List.of("실내", "주간"),
                List.of("소음"),
                List.of("휠체어 접근"),
                ProfileDisabilityType.PHYSICAL,
                "사무 경력 3년",
                "대졸",
                "정규직",
                "테스트 프로필",

                "홍길동",
                "010-1111-2222",
                "hong@example.com",
                LocalDate.of(1995, 5, 10),
                GenderType.MALE,
                null,
                "강남구",
                null,

                ProfileHighestEducation.BACHELOR,
                ProfileGraduationStatus.GRADUATED,
                "A사 사무보조",
                null,
                null,
                null,

                "사무보조",
                List.of("엑셀", "문서작성"),
                List.of("컴활"),
                null,
                null,
                null,
                ProfileDisabilitySeverity.SEVERE,
                true,
                null,
                null,
                null,

                ProfileWorkAvailability.IMMEDIATE,
                List.of(ProfileWorkType.FULL_TIME),
                null,
                null,
                false,
                null,

                "문서 관리에 강점이 있습니다.",
                null,
                null,
                null,
                null,

                null,
                false,
                null,
                null
        );
    }
}
