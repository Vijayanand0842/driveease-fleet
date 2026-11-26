package com.driveease.booking.dto;

import com.driveease.booking.entity.Booking;
import com.driveease.booking.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        Long id,
        Long userId,
        String customerEmail,
        String customerName,
        Long vehicleId,
        String vehicleSummary,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate actualReturnDate,
        BigDecimal dailyRate,
        BigDecimal totalCost,
        BigDecimal lateFee,
        BookingStatus status,
        String notes,
        Integer returnMileage,
        Integer returnFuelPercent,
        Boolean damageReported,
        Instant createdAt
) {
    public static BookingResponse from(Booking b) {
        return new BookingResponse(
                b.getId(),
                b.getUserId(),
                b.getCustomerEmail(),
                b.getCustomerName(),
                b.getVehicleId(),
                b.getVehicleSummary(),
                b.getStartDate(),
                b.getEndDate(),
                b.getActualReturnDate(),
                b.getDailyRate(),
                b.getTotalCost(),
                b.getLateFee(),
                b.getStatus(),
                b.getNotes(),
                b.getReturnMileage(),
                b.getReturnFuelPercent(),
                b.getDamageReported(),
                b.getCreatedAt()
        );
    }
}
