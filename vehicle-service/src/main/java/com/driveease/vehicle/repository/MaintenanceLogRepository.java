package com.driveease.vehicle.repository;

import com.driveease.vehicle.entity.MaintenanceLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceLogRepository extends JpaRepository<MaintenanceLog, Long> {
    List<MaintenanceLog> findByVehicleIdOrderByScheduledDateDesc(Long vehicleId);
}
