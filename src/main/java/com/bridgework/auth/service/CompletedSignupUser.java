package com.bridgework.auth.service;

import com.bridgework.auth.entity.UserRole;

public record CompletedSignupUser(Long userId, String email, UserRole role) {
}
