package com.driveease.booking.client;

import java.math.BigDecimal;

public record VehicleSummaryDto(
        Long id,
        String registrationNumber,
        String make,
        String model,
        int modelYear,
        String category,
        BigDecimal dailyRate,
        int mileage,
        int fuelLevelPercent,
        String locationHub,
        String status
) {}
