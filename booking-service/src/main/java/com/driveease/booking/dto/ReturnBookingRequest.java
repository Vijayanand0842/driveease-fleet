package com.driveease.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

public record ReturnBookingRequest(
        LocalDate actualReturnDate,

        @Min(0)
        Integer returnMileage,

        @Min(0) @Max(100)
        Integer returnFuelPercent,

        boolean needsMaintenance,
        String notes
) {}
