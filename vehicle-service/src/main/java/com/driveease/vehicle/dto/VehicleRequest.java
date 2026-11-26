package com.driveease.vehicle.dto;

import com.driveease.vehicle.entity.VehicleCategory;
import com.driveease.vehicle.entity.VehicleStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record VehicleRequest(
        @NotBlank(message = "VIN is required")
        String vin,

        @NotBlank(message = "Registration number is required")
        String registrationNumber,

        @NotBlank(message = "Make is required")
        String make,

        @NotBlank(message = "Model is required")
        String model,

        @Min(2005) @Max(2030)
        int modelYear,

        @NotNull(message = "Category is required")
        VehicleCategory category,

        @NotNull(message = "Daily rate is required")
        @DecimalMin(value = "1.00", message = "Daily rate must be positive")
        BigDecimal dailyRate,

        @Min(0)
        int mileage,

        @Min(0) @Max(100)
        int fuelLevelPercent,

        @NotBlank(message = "Location hub is required")
        String locationHub,

        VehicleStatus status
) {}
