package com.bridgework.auth.security;

import com.bridgework.auth.entity.UserRole;

public record ParsedJwtToken(
        Long userId,
        UserRole role,
        String tokenId,
        String tokenType
) {
}
