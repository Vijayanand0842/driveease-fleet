package com.driveease.booking.client;

public record UserSummaryDto(
        Long id,
        String fullName,
        String email,
        String role,
        boolean active
) {}
