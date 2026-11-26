package com.driveease.vehicle.dto;

import com.driveease.vehicle.entity.VehicleStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VehicleStatusUpdateRequest(
        @NotNull(message = "Status is required")
        VehicleStatus status,

        @Min(0)
        Integer mileage,

        @Min(0) @Max(100)
        Integer fuelLevelPercent
) {}
