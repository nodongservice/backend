package com.bridgework.auth.service;

import com.bridgework.auth.entity.SocialProvider;

public record SocialUserProfile(
        SocialProvider provider,
        String providerUserId,
        String email,
        String name
) {
}
