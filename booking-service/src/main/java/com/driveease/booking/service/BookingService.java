package com.driveease.booking.service;

import com.driveease.booking.client.*;
import com.driveease.booking.dto.*;
import com.driveease.booking.entity.Booking;
import com.driveease.booking.entity.BookingStatus;
import com.driveease.booking.repository.BookingRepository;
import com.driveease.common.security.JwtUserPrincipal;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class BookingService {

    private static final List<BookingStatus> ACTIVE_RESERVATION_STATUSES =
            List.of(BookingStatus.CONFIRMED, BookingStatus.ACTIVE);

    private final BookingRepository bookingRepository;
    private final VehicleClient vehicleClient;
    private final UserClient userClient;
    private final ConcurrentHashMap<Long, ReentrantLock> vehicleLocks = new ConcurrentHashMap<>();

    public BookingService(BookingRepository bookingRepository,
                          VehicleClient vehicleClient,
                          UserClient userClient) {
        this.bookingRepository = bookingRepository;
        this.vehicleClient = vehicleClient;
        this.userClient = userClient;
    }

    public AvailabilityResponse checkAvailability(Long vehicleId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate, false);
        VehicleSummaryDto vehicle = fetchVehicle(vehicleId);
        if ("MAINTENANCE".equalsIgnoreCase(vehicle.status()) || "RETIRED".equalsIgnoreCase(vehicle.status())) {
            return new AvailabilityResponse(vehicleId, startDate, endDate, false,
                    "Vehicle is currently in " + vehicle.status() + " status");
        }
        List<Booking> overlaps = bookingRepository.findOverlappingBookings(
                vehicleId, startDate, endDate, ACTIVE_RESERVATION_STATUSES);
        if (!overlaps.isEmpty()) {
            Booking first = overlaps.get(0);
            return new AvailabilityResponse(vehicleId, startDate, endDate, false,
                    "Overlaps with reservation #" + first.getId()
                            + " (" + first.getStartDate() + " to " + first.getEndDate() + ")");
        }
        return new AvailabilityResponse(vehicleId, startDate, endDate, true, "Available for selected dates");
    }

    public List<ReservedWindowDto> getVehicleCalendar(Long vehicleId) {
        return bookingRepository.findByVehicleIdAndStatusInOrderByStartDateAsc(vehicleId, ACTIVE_RESERVATION_STATUSES)
                .stream()
                .map(b -> new ReservedWindowDto(b.getId(), b.getStartDate(), b.getEndDate(), b.getStatus().name()))
                .toList();
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest req, JwtUserPrincipal principal) {
        validateDateRange(req.startDate(), req.endDate(), true);

        UserSummaryDto customer = fetchUser(principal.userId());
        if (!customer.active()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is inactive");
        }

        ReentrantLock lock = vehicleLocks.computeIfAbsent(req.vehicleId(), id -> new ReentrantLock());
        lock.lock();
        try {
            VehicleSummaryDto vehicle = fetchVehicle(req.vehicleId());
            if ("MAINTENANCE".equalsIgnoreCase(vehicle.status()) || "RETIRED".equalsIgnoreCase(vehicle.status())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Vehicle cannot be booked while in " + vehicle.status() + " status");
            }

            List<Booking> overlaps = bookingRepository.findOverlappingBookings(
                    req.vehicleId(), req.startDate(), req.endDate(), ACTIVE_RESERVATION_STATUSES);
            if (!overlaps.isEmpty()) {
                Booking conflict = overlaps.get(0);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Vehicle already reserved from " + conflict.getStartDate() + " to " + conflict.getEndDate());
            }

            long days = ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1;
            BigDecimal totalCost = vehicle.dailyRate()
                    .multiply(BigDecimal.valueOf(days))
                    .setScale(2, RoundingMode.HALF_UP);

            Booking booking = new Booking();
            booking.setUserId(customer.id());
            booking.setCustomerEmail(customer.email());
            booking.setCustomerName(customer.fullName());
            booking.setVehicleId(vehicle.id());
            booking.setVehicleSummary(vehicle.modelYear() + " " + vehicle.make() + " " + vehicle.model()
                    + " (" + vehicle.registrationNumber() + ")");
            booking.setStartDate(req.startDate());
            booking.setEndDate(req.endDate());
            booking.setDailyRate(vehicle.dailyRate());
            booking.setTotalCost(totalCost);
            booking.setLateFee(BigDecimal.ZERO);
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setNotes(req.notes());

            return BookingResponse.from(bookingRepository.save(booking));
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public BookingResponse pickupBooking(Long bookingId, JwtUserPrincipal principal) {
        Booking booking = findBookingAndAuthorize(bookingId, principal);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only CONFIRMED bookings can be picked up; current status: " + booking.getStatus());
        }
        VehicleSummaryDto vehicle = fetchVehicle(booking.getVehicleId());
        if ("MAINTENANCE".equalsIgnoreCase(vehicle.status()) || "RETIRED".equalsIgnoreCase(vehicle.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vehicle is currently in " + vehicle.status() + " and cannot be dispatched");
        }
        updateVehicleStatus(booking.getVehicleId(), new VehicleStatusUpdateDto("RENTED", null, null));
        booking.setStatus(BookingStatus.ACTIVE);
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse returnBooking(Long bookingId, ReturnBookingRequest req, JwtUserPrincipal principal) {
        Booking booking = findBookingAndAuthorize(bookingId, principal);
        if (booking.getStatus() != BookingStatus.ACTIVE && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking cannot be returned from status: " + booking.getStatus());
        }

        LocalDate returnDate = (req != null && req.actualReturnDate() != null)
                ? req.actualReturnDate()
                : LocalDate.now();

        BigDecimal lateFee = BigDecimal.ZERO;
        if (returnDate.isAfter(booking.getEndDate())) {
            long lateDays = ChronoUnit.DAYS.between(booking.getEndDate(), returnDate);
            lateFee = booking.getDailyRate()
                    .multiply(BigDecimal.valueOf(lateDays))
                    .multiply(new BigDecimal("1.50"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        boolean needsMaintenance = req != null && req.needsMaintenance();
        String targetVehicleStatus = needsMaintenance ? "MAINTENANCE" : "AVAILABLE";
        Integer mileage = req != null ? req.returnMileage() : null;
        Integer fuel = req != null ? req.returnFuelPercent() : null;

        updateVehicleStatus(booking.getVehicleId(), new VehicleStatusUpdateDto(targetVehicleStatus, mileage, fuel));

        booking.setActualReturnDate(returnDate);
        booking.setLateFee(lateFee);
        booking.setReturnMileage(mileage);
        booking.setReturnFuelPercent(fuel);
        booking.setDamageReported(needsMaintenance);
        if (req != null && req.notes() != null && !req.notes().isBlank()) {
            booking.setNotes(req.notes().trim());
        }
        booking.setStatus(BookingStatus.RETURNED);

        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId, JwtUserPrincipal principal) {
        Booking booking = findBookingAndAuthorize(bookingId, principal);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only CONFIRMED bookings can be cancelled; current status: " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.CANCELLED);
        return BookingResponse.from(bookingRepository.save(booking));
    }

    public List<BookingResponse> myBookings(Long userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(BookingResponse::from)
                .toList();
    }

    public List<BookingResponse> allBookings() {
        return bookingRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(BookingResponse::from)
                .toList();
    }

    public BookingResponse getBooking(Long id, JwtUserPrincipal principal) {
        return BookingResponse.from(findBookingAndAuthorize(id, principal));
    }

    private Booking findBookingAndAuthorize(Long id, JwtUserPrincipal principal) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + id));
        boolean privileged = principal.hasAnyRole("ADMIN", "FLEET_MANAGER");
        if (!privileged && !booking.getUserId().equals(principal.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to access this booking");
        }
        return booking;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate, boolean enforceNotPast) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date and end date are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot be before start date");
        }
        if (enforceNotPast && startDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date cannot be in the past");
        }
    }

    private VehicleSummaryDto fetchVehicle(Long vehicleId) {
        try {
            return vehicleClient.getVehicle(vehicleId);
        } catch (FeignException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found: " + vehicleId);
        } catch (FeignException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Vehicle service unavailable");
        }
    }

    private UserSummaryDto fetchUser(Long userId) {
        try {
            return userClient.getUser(userId);
        } catch (FeignException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        } catch (FeignException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
        }
    }

    private void updateVehicleStatus(Long vehicleId, VehicleStatusUpdateDto dto) {
        try {
            vehicleClient.updateStatus(vehicleId, dto);
        } catch (FeignException.BadRequest ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid vehicle telematics update: " + ex.contentUTF8());
        } catch (FeignException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Failed to update vehicle status");
        }
    }
}
