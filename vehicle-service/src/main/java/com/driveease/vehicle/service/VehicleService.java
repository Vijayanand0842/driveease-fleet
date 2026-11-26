package com.driveease.vehicle.service;

import com.driveease.vehicle.dto.*;
import com.driveease.vehicle.entity.*;
import com.driveease.vehicle.repository.MaintenanceLogRepository;
import com.driveease.vehicle.repository.VehicleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;

    public VehicleService(VehicleRepository vehicleRepository,
                          MaintenanceLogRepository maintenanceLogRepository) {
        this.vehicleRepository = vehicleRepository;
        this.maintenanceLogRepository = maintenanceLogRepository;
    }

    public List<VehicleResponse> search(VehicleStatus status, VehicleCategory category, String hub, String search) {
        String normalizedHub = (hub == null || hub.isBlank()) ? null : hub.trim();
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        return vehicleRepository.searchFleet(status, category, normalizedHub, normalizedSearch)
                .stream()
                .map(VehicleResponse::from)
                .toList();
    }

    public List<VehicleResponse> listAvailable(boolean includeCurrentlyRented) {
        List<VehicleStatus> statuses = includeCurrentlyRented
                ? List.of(VehicleStatus.AVAILABLE, VehicleStatus.RENTED)
                : List.of(VehicleStatus.AVAILABLE);
        return vehicleRepository.findByStatusInOrderByIdAsc(statuses)
                .stream()
                .map(VehicleResponse::from)
                .toList();
    }

    public VehicleResponse getById(Long id) {
        return VehicleResponse.from(findVehicle(id));
    }

    public FleetSummaryResponse summary() {
        return new FleetSummaryResponse(
                vehicleRepository.count(),
                vehicleRepository.countByStatus(VehicleStatus.AVAILABLE),
                vehicleRepository.countByStatus(VehicleStatus.RENTED),
                vehicleRepository.countByStatus(VehicleStatus.MAINTENANCE),
                vehicleRepository.countByStatus(VehicleStatus.RETIRED)
        );
    }

    @Transactional
    public VehicleResponse create(VehicleRequest req) {
        if (vehicleRepository.existsByVinIgnoreCase(req.vin())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "VIN already exists: " + req.vin());
        }
        if (vehicleRepository.existsByRegistrationNumberIgnoreCase(req.registrationNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Registration already exists: " + req.registrationNumber());
        }
        Vehicle v = new Vehicle();
        applyFields(v, req);
        v.setStatus(req.status() != null ? req.status() : VehicleStatus.AVAILABLE);
        return VehicleResponse.from(vehicleRepository.save(v));
    }

    @Transactional
    public VehicleResponse update(Long id, VehicleRequest req) {
        Vehicle v = findVehicle(id);
        applyFields(v, req);
        if (req.status() != null) {
            v.setStatus(req.status());
        }
        return VehicleResponse.from(vehicleRepository.save(v));
    }

    @Transactional
    public VehicleResponse updateOperationalStatus(Long id, VehicleStatusUpdateRequest req) {
        Vehicle v = findVehicle(id);
        v.setStatus(req.status());
        if (req.mileage() != null) {
            if (req.mileage() < v.getMileage()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mileage cannot decrease below " + v.getMileage());
            }
            v.setMileage(req.mileage());
        }
        if (req.fuelLevelPercent() != null) {
            v.setFuelLevelPercent(req.fuelLevelPercent());
        }
        return VehicleResponse.from(vehicleRepository.save(v));
    }

    @Transactional
    public MaintenanceResponse logMaintenance(Long vehicleId, MaintenanceRequest req, String actorEmail) {
        Vehicle v = findVehicle(vehicleId);
        MaintenanceLog log = new MaintenanceLog();
        log.setVehicleId(vehicleId);
        log.setMaintenanceType(req.maintenanceType().trim());
        log.setDescription(req.description().trim());
        log.setScheduledDate(req.scheduledDate());
        log.setCompletedDate(req.completedDate());
        log.setPerformedBy(req.performedBy() != null && !req.performedBy().isBlank() ? req.performedBy() : actorEmail);

        if (req.markVehicleInMaintenance()) {
            v.setStatus(VehicleStatus.MAINTENANCE);
        }
        if (req.completedDate() != null) {
            v.setLastMaintenanceDate(req.completedDate());
            if (!req.markVehicleInMaintenance() && v.getStatus() == VehicleStatus.MAINTENANCE) {
                v.setStatus(VehicleStatus.AVAILABLE);
            }
        } else if (v.getLastMaintenanceDate() == null) {
            v.setLastMaintenanceDate(LocalDate.now());
        }
        vehicleRepository.save(v);
        return MaintenanceResponse.from(maintenanceLogRepository.save(log));
    }

    public List<MaintenanceResponse> getMaintenanceLogs(Long vehicleId) {
        findVehicle(vehicleId);
        return maintenanceLogRepository.findByVehicleIdOrderByScheduledDateDesc(vehicleId)
                .stream()
                .map(MaintenanceResponse::from)
                .toList();
    }

    private Vehicle findVehicle(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found: " + id));
    }

    private void applyFields(Vehicle v, VehicleRequest req) {
        v.setVin(req.vin().trim().toUpperCase());
        v.setRegistrationNumber(req.registrationNumber().trim().toUpperCase());
        v.setMake(req.make().trim());
        v.setModel(req.model().trim());
        v.setModelYear(req.modelYear());
        v.setCategory(req.category());
        v.setDailyRate(req.dailyRate());
        v.setMileage(req.mileage());
        v.setFuelLevelPercent(req.fuelLevelPercent());
        v.setLocationHub(req.locationHub().trim());
    }
}
