package com.driveease.vehicle;

import com.driveease.common.security.JwtService;
import com.driveease.vehicle.dto.VehicleRequest;
import com.driveease.vehicle.dto.VehicleStatusUpdateRequest;
import com.driveease.vehicle.entity.VehicleCategory;
import com.driveease.vehicle.entity.VehicleStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VehicleControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;

    @Test
    void fleetListingAndRoleProtectedCreation() throws Exception {
        mockMvc.perform(get("/api/vehicles/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(8));

        String customerToken = jwtService.generateToken(10L, "cust@test.com", "Customer", "CUSTOMER");
        String managerToken = jwtService.generateToken(2L, "mgr@test.com", "Manager", "FLEET_MANAGER");

        var createReq = new VehicleRequest(
                "DE9VIN00000000099", "TX-DE-999", "Kia", "EV6", 2025,
                VehicleCategory.EV, new BigDecimal("89.50"), 1500, 98, "Austin Hub", VehicleStatus.AVAILABLE
        );

        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/vehicles")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationNumber").value("TX-DE-999"));

        var invalidMileageReq = new VehicleStatusUpdateRequest(VehicleStatus.MAINTENANCE, 1600, 95);
        mockMvc.perform(patch("/api/vehicles/1/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidMileageReq)))
                .andExpect(status().isBadRequest());

        var statusReq = new VehicleStatusUpdateRequest(VehicleStatus.MAINTENANCE, 14500, 95);
        mockMvc.perform(patch("/api/vehicles/1/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }
}
