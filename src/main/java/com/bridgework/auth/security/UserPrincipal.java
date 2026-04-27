package com.bridgework.auth.security;

import com.bridgework.auth.entity.UserRole;

public class UserPrincipal {

    private final Long userId;
    private final UserRole role;

    public UserPrincipal(Long userId, UserRole role) {
        this.userId = userId;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public UserRole getRole() {
        return role;
    }
}
