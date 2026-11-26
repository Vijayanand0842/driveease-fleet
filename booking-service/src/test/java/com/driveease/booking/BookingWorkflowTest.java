package com.driveease.booking;

import com.driveease.booking.client.*;
import com.driveease.booking.dto.CreateBookingRequest;
import com.driveease.booking.dto.ReturnBookingRequest;
import com.driveease.common.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingWorkflowTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;

    @MockitoBean VehicleClient vehicleClient;
    @MockitoBean UserClient userClient;

    private String customerToken;

    @BeforeEach
    void setup() {
        customerToken = jwtService.generateToken(101L, "alex@driveease.com", "Alex Rivera", "CUSTOMER");

        when(userClient.getUser(101L)).thenReturn(
                new UserSummaryDto(101L, "Alex Rivera", "alex@driveease.com", "CUSTOMER", true));

        when(vehicleClient.getVehicle(1L)).thenReturn(
                new VehicleSummaryDto(1L, "WA-DE-101", "Toyota", "Camry Hybrid", 2024,
                        "SEDAN", new BigDecimal("70.00"), 14000, 90, "Seattle Hub", "AVAILABLE"));

        when(vehicleClient.getVehicle(7L)).thenReturn(
                new VehicleSummaryDto(7L, "CO-DE-402", "Subaru", "Outback AWD", 2024,
                        "SUV", new BigDecimal("82.00"), 18400, 65, "Denver Hub", "MAINTENANCE"));

        when(vehicleClient.updateStatus(eq(1L), any(VehicleStatusUpdateDto.class))).thenReturn(
                new VehicleSummaryDto(1L, "WA-DE-101", "Toyota", "Camry Hybrid", 2024,
                        "SEDAN", new BigDecimal("70.00"), 14250, 85, "Seattle Hub", "AVAILABLE"));
    }

    @Test
    void fullOverlapPreventionPickupAndLateReturnLifecycle() throws Exception {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(3);

        // 1. Create booking for 3 days => $210.00
        MvcResult createRes = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(1L, start, end, "Client trip"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalCost").value(210.00))
                .andReturn();

        JsonNode createdJson = objectMapper.readTree(createRes.getResponse().getContentAsString());
        long bookingId = createdJson.get("id").asLong();

        // 2. Overlapping booking must be rejected with 409 Conflict
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateBookingRequest(1L, start.plusDays(1), end.plusDays(2), "Overlap attempt"))))
                .andExpect(status().isConflict());

        // 3. Availability endpoint reports conflict on overlapping dates, open on later dates
        mockMvc.perform(get("/api/bookings/availability")
                        .param("vehicleId", "1")
                        .param("startDate", start.plusDays(1).toString())
                        .param("endDate", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(get("/api/bookings/availability")
                        .param("vehicleId", "1")
                        .param("startDate", end.plusDays(1).toString())
                        .param("endDate", end.plusDays(3).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        // 4. Vehicle in MAINTENANCE cannot be booked
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateBookingRequest(7L, start, end, "Maint attempt"))))
                .andExpect(status().isConflict());

        // 5. Pickup transitions booking to ACTIVE and updates vehicle to RENTED
        mockMvc.perform(post("/api/bookings/" + bookingId + "/pickup")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(vehicleClient).updateStatus(1L, new VehicleStatusUpdateDto("RENTED", null, null));

        // 6. Late return (1 day past endDate) computes lateFee = 70 * 1.5 = 105.00 and restores vehicle AVAILABLE
        var returnReq = new ReturnBookingRequest(end.plusDays(1), 14250, 85, false, "Returned clean");
        mockMvc.perform(post("/api/bookings/" + bookingId + "/return")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.lateFee").value(105.00));

        verify(vehicleClient).updateStatus(1L, new VehicleStatusUpdateDto("AVAILABLE", 14250, 85));
    }

    @Test
    void cancellingBookingReleasesDateWindow() throws Exception {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = LocalDate.now().plusDays(12);

        MvcResult res = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(1L, start, end, "Tentative"))))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/bookings/" + id + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Re-booking the exact same window now succeeds
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(1L, start, end, "Rebooked"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}
