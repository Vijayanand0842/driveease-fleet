package com.driveease.booking.dto;

import java.time.LocalDate;

public record ReservedWindowDto(
        Long bookingId,
        LocalDate startDate,
        LocalDate endDate,
        String status
) {}
