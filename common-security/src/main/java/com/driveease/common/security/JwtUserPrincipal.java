package com.driveease.common.security;

/**
 * Immutable authenticated identity extracted from a validated JWT.
 */
public record JwtUserPrincipal(
        Long userId,
        String email,
        String fullName,
        String role
) {
    public boolean hasAnyRole(String... allowedRoles) {
        if (role == null) {
            return false;
        }
        for (String allowed : allowedRoles) {
            if (role.equalsIgnoreCase(allowed)) {
                return true;
            }
        }
        return false;
    }
}
