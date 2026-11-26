package com.driveease.booking.controller;

import com.driveease.booking.dto.*;
import com.driveease.booking.service.BookingService;
import com.driveease.common.security.JwtUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/availability")
    public AvailabilityResponse checkAvailability(
            @RequestParam Long vehicleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return bookingService.checkAvailability(vehicleId, startDate, endDate);
    }

    @GetMapping("/vehicle/{vehicleId}/calendar")
    public List<ReservedWindowDto> vehicleCalendar(@PathVariable Long vehicleId) {
        return bookingService.getVehicleCalendar(vehicleId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest request,
                                  @AuthenticationPrincipal JwtUserPrincipal principal) {
        return bookingService.createBooking(request, principal);
    }

    @PostMapping("/{id}/pickup")
    public BookingResponse pickup(@PathVariable Long id,
                                  @AuthenticationPrincipal JwtUserPrincipal principal) {
        return bookingService.pickupBooking(id, principal);
    }

    @PostMapping("/{id}/return")
    public BookingResponse returnVehicle(@PathVariable Long id,
                                         @Valid @RequestBody(required = false) ReturnBookingRequest request,
                                         @AuthenticationPrincipal JwtUserPrincipal principal) {
        return bookingService.returnBooking(id, request, principal);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id,
                                  @AuthenticationPrincipal JwtUserPrincipal principal) {
        return bookingService.cancelBooking(id, principal);
    }

    @GetMapping("/my")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return bookingService.myBookings(principal.userId());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FLEET_MANAGER')")
    public List<BookingResponse> allBookings() {
        return bookingService.allBookings();
    }

    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable Long id,
                                   @AuthenticationPrincipal JwtUserPrincipal principal) {
        return bookingService.getBooking(id, principal);
    }
}
