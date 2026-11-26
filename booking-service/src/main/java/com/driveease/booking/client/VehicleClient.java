package com.driveease.booking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "VEHICLE-SERVICE")
public interface VehicleClient {

    @GetMapping("/api/vehicles/{id}")
    VehicleSummaryDto getVehicle(@PathVariable("id") Long id);

    @PutMapping("/api/vehicles/{id}/status")
    VehicleSummaryDto updateStatus(@PathVariable("id") Long id, @RequestBody VehicleStatusUpdateDto request);
}
