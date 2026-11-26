package com.driveease.booking.client;

public record VehicleStatusUpdateDto(
        String status,
        Integer mileage,
        Integer fuelLevelPercent
) {}
