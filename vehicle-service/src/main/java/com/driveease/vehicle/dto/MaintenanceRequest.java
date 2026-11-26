package com.driveease.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record MaintenanceRequest(
        @NotBlank(message = "Maintenance type is required")
        String maintenanceType,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Scheduled date is required")
        LocalDate scheduledDate,

        LocalDate completedDate,
        String performedBy,
        boolean markVehicleInMaintenance
) {}
