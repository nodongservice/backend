package com.bridgework.onboarding.service;

import com.bridgework.onboarding.dto.OnboardingProfileUpsertRequestDto;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OnboardingAiTagService {

    public OnboardingAiTags buildTags(OnboardingProfileUpsertRequestDto request) {
        List<String> jobTags = mergeUnique(
                List.of(request.desiredJob(), request.targetJob(), request.careerSummary(), request.educationSummary()),
                request.skills(),
                request.certifications()
        );

        List<String> environmentTags = mergeUnique(
                request.preferredWorkEnvironments(),
                request.avoidedWorkEnvironments(),
                List.of(request.workAvailability(), request.disabilitySeverity(), request.commuteRange())
        );

        List<String> supportTags = mergeUnique(
                request.requiredSupports(),
                List.of(request.disabilityType(), request.workSupportRequirements(), request.assistiveDevices())
        );

        return new OnboardingAiTags(jobTags, environmentTags, supportTags);
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
