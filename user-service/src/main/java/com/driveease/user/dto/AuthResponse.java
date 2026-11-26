package com.driveease.user.dto;

public record AuthResponse(
        String token,
        String tokenType,
        UserResponse user
) {}
