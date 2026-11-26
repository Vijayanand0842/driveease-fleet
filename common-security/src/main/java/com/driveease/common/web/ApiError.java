package com.driveease.common.web;

import java.time.Instant;

/**
 * Standard JSON error payload returned by all DriveEase microservices.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path);
    }
}
