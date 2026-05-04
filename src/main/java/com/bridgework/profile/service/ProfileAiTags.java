package com.bridgework.profile.service;

import java.util.List;

public record ProfileAiTags(
        List<String> jobTags,
        List<String> environmentTags,
        List<String> supportTags
) {
}
