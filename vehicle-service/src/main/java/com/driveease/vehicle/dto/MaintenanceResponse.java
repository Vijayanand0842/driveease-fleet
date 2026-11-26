package com.driveease.vehicle.dto;

import com.driveease.vehicle.entity.MaintenanceLog;

import java.time.Instant;
import java.time.LocalDate;

public record MaintenanceResponse(
        Long id,
        Long vehicleId,
        String maintenanceType,
        String description,
        LocalDate scheduledDate,
        LocalDate completedDate,
        String performedBy,
        Instant createdAt
) {
    public static MaintenanceResponse from(MaintenanceLog log) {
        return new MaintenanceResponse(
                log.getId(),
                log.getVehicleId(),
                log.getMaintenanceType(),
                log.getDescription(),
                log.getScheduledDate(),
                log.getCompletedDate(),
                log.getPerformedBy(),
                log.getCreatedAt()
        );
    }
}
