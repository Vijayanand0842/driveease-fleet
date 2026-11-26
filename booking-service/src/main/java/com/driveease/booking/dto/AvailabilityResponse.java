package com.driveease.booking.dto;

import java.time.LocalDate;

public record AvailabilityResponse(
        Long vehicleId,
        LocalDate startDate,
        LocalDate endDate,
        boolean available,
        String reason
) {}
