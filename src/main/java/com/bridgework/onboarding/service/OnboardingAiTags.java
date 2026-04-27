package com.bridgework.onboarding.service;

import java.util.List;

public record OnboardingAiTags(
        List<String> jobTags,
        List<String> environmentTags,
        List<String> supportTags
) {
}
