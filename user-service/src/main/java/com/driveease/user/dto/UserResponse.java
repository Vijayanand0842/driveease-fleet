package com.driveease.user.dto;

import com.driveease.user.entity.User;
import com.driveease.user.entity.UserRole;

import java.time.Instant;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String driverLicenseNo,
        UserRole role,
        boolean active,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getDriverLicenseNo(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
