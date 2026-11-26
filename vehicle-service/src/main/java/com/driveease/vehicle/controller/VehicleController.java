package com.driveease.vehicle.controller;

import com.driveease.common.security.JwtUserPrincipal;
import com.driveease.vehicle.dto.*;
import com.driveease.vehicle.entity.VehicleCategory;
import com.driveease.vehicle.entity.VehicleStatus;
import com.driveease.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<VehicleResponse> list(
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false) VehicleCategory category,
            @RequestParam(required = false) String hub,
            @RequestParam(required = false) String search) {
        return vehicleService.search(status, category, hub, search);
    }

    @GetMapping("/available")
    public List<VehicleResponse> available(
            @RequestParam(defaultValue = "false") boolean includeRented) {
        return vehicleService.listAvailable(includeRented);
    }

    @GetMapping("/summary")
    public FleetSummaryResponse summary() {
        return vehicleService.summary();
    }

    @GetMapping("/{id}")
    public VehicleResponse getById(@PathVariable Long id) {
        return vehicleService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER')")
    public VehicleResponse create(@Valid @RequestBody VehicleRequest request) {
        return vehicleService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER')")
    public VehicleResponse update(@PathVariable Long id, @Valid @RequestBody VehicleRequest request) {
        return vehicleService.update(id, request);
    }

    @RequestMapping(value = "/{id}/status", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("isAuthenticated()")
    public VehicleResponse updateStatus(@PathVariable Long id,
                                        @Valid @RequestBody VehicleStatusUpdateRequest request) {
        return vehicleService.updateOperationalStatus(id, request);
    }

    @GetMapping("/{id}/maintenance")
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER')")
    public List<MaintenanceResponse> getMaintenance(@PathVariable Long id) {
        return vehicleService.getMaintenanceLogs(id);
    }

    @PostMapping("/{id}/maintenance")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER')")
    public MaintenanceResponse addMaintenance(@PathVariable Long id,
                                              @Valid @RequestBody MaintenanceRequest request,
                                              @AuthenticationPrincipal JwtUserPrincipal principal) {
        return vehicleService.logMaintenance(id, request, principal != null ? principal.email() : "system");
    }
}
