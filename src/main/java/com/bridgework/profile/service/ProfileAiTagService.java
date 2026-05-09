package com.bridgework.profile.service;

import com.bridgework.profile.dto.UserProfileUpsertRequestDto;
import com.bridgework.profile.enums.LabeledEnum;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProfileAiTagService {

    public ProfileAiTags buildTags(UserProfileUpsertRequestDto request) {
        List<String> jobTags = mergeUnique(
                nullableValues(request.desiredJob(), request.targetJob(), request.careerSummary(), request.educationSummary()),
                request.skills(),
                request.certifications()
        );

        List<String> environmentTags = mergeUnique(
                request.preferredWorkEnvironments(),
                request.avoidedWorkEnvironments(),
                nullableValues(
                        enumLabel(request.workAvailability()),
                        enumLabel(request.disabilitySeverity()),
                        request.commuteRange()
                )
        );

        List<String> supportTags = mergeUnique(
                request.requiredSupports(),
                nullableValues(
                        enumLabel(request.disabilityType()),
                        request.workSupportRequirements(),
                        request.assistiveDevices()
                )
        );

        return new ProfileAiTags(jobTags, environmentTags, supportTags);
    }

    private List<String> nullableValues(String... values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        return new ArrayList<>(Arrays.asList(values));
    }

    private String enumLabel(LabeledEnum value) {
        return value == null ? null : ((Enum<?>) value).name();
    }

    @SafeVarargs
    private List<String> mergeUnique(List<String>... sources) {
        // 추천 근거를 설명 가능하게 유지하기 위해 입력 순서를 보존한 중복 제거를 적용한다.
        Set<String> merged = new LinkedHashSet<>();

        for (List<String> source : sources) {
            if (source == null) {
                continue;
            }
            for (String value : source) {
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                String normalized = value.trim();
                if (!normalized.isEmpty()) {
                    merged.add(normalized);
                }
            }
        }

        return new ArrayList<>(merged);
    }
}
