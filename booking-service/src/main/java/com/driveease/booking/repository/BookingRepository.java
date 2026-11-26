package com.driveease.booking.repository;

import com.driveease.booking.entity.Booking;
import com.driveease.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Booking> findAllByOrderByCreatedAtDesc();

    List<Booking> findByVehicleIdAndStatusInOrderByStartDateAsc(Long vehicleId, List<BookingStatus> statuses);

    @Query("""
            SELECT b FROM Booking b
            WHERE b.vehicleId = :vehicleId
              AND b.status IN :activeStatuses
              AND b.startDate <= :endDate
              AND b.endDate >= :startDate
            ORDER BY b.startDate ASC
            """)
    List<Booking> findOverlappingBookings(@Param("vehicleId") Long vehicleId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate,
                                          @Param("activeStatuses") List<BookingStatus> activeStatuses);
}
