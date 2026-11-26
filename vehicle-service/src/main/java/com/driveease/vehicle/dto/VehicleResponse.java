package com.driveease.vehicle.dto;

import com.driveease.vehicle.entity.Vehicle;
import com.driveease.vehicle.entity.VehicleCategory;
import com.driveease.vehicle.entity.VehicleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record VehicleResponse(
        Long id,
        String vin,
        String registrationNumber,
        String make,
        String model,
        int modelYear,
        VehicleCategory category,
        BigDecimal dailyRate,
        int mileage,
        int fuelLevelPercent,
        String locationHub,
        VehicleStatus status,
        LocalDate lastMaintenanceDate,
        Instant updatedAt
) {
    public static VehicleResponse from(Vehicle v) {
        return new VehicleResponse(
                v.getId(),
                v.getVin(),
                v.getRegistrationNumber(),
                v.getMake(),
                v.getModel(),
                v.getModelYear(),
                v.getCategory(),
                v.getDailyRate(),
                v.getMileage(),
                v.getFuelLevelPercent(),
                v.getLocationHub(),
                v.getStatus(),
                v.getLastMaintenanceDate(),
                v.getUpdatedAt()
        );
    }
}
