package com.driveease.vehicle.dto;

public record FleetSummaryResponse(
        long total,
        long available,
        long rented,
        long maintenance,
        long retired
) {}
