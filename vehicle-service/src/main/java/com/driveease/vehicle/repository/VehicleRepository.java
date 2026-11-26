package com.driveease.vehicle.repository;

import com.driveease.vehicle.entity.Vehicle;
import com.driveease.vehicle.entity.VehicleCategory;
import com.driveease.vehicle.entity.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    boolean existsByVinIgnoreCase(String vin);

    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);

    long countByStatus(VehicleStatus status);

    @Query("""
            SELECT v FROM Vehicle v
            WHERE (:status IS NULL OR v.status = :status)
              AND (:category IS NULL OR v.category = :category)
              AND (:hub IS NULL OR LOWER(v.locationHub) = LOWER(:hub))
              AND (:search IS NULL OR LOWER(v.make) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(v.model) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(v.registrationNumber) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY v.id ASC
            """)
    List<Vehicle> searchFleet(@Param("status") VehicleStatus status,
                              @Param("category") VehicleCategory category,
                              @Param("hub") String hub,
                              @Param("search") String search);

    @Query("""
            SELECT v FROM Vehicle v
            WHERE v.status IN :statuses
            ORDER BY v.id ASC
            """)
    List<Vehicle> findByStatusInOrderByIdAsc(@Param("statuses") List<VehicleStatus> statuses);
}
