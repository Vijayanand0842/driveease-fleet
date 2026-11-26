package com.driveease.vehicle.config;

import com.driveease.vehicle.entity.Vehicle;
import com.driveease.vehicle.entity.VehicleCategory;
import com.driveease.vehicle.entity.VehicleStatus;
import com.driveease.vehicle.repository.VehicleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedFleet(VehicleRepository repo) {
        return args -> {
            if (repo.count() > 0) {
                return;
            }
            repo.saveAll(List.of(
                    build("DE1VIN00000000001", "WA-DE-101", "Toyota", "Camry Hybrid", 2024,
                            VehicleCategory.SEDAN, "68.00", 14200, 92, "Seattle Hub", VehicleStatus.AVAILABLE),
                    build("DE1VIN00000000002", "WA-DE-102", "Ford", "Transit 250", 2024,
                            VehicleCategory.VAN, "115.00", 28900, 84, "Seattle Hub", VehicleStatus.AVAILABLE),
                    build("DE1VIN00000000003", "TX-DE-201", "Tesla", "Model Y Long Range", 2025,
                            VehicleCategory.EV, "98.00", 6400, 96, "Austin Hub", VehicleStatus.AVAILABLE),
                    build("DE1VIN00000000004", "TX-DE-202", "Chevrolet", "Tahoe Fleet", 2024,
                            VehicleCategory.SUV, "108.00", 21500, 78, "Austin Hub", VehicleStatus.AVAILABLE),
                    build("DE1VIN00000000005", "IL-DE-301", "Ram", "ProMaster 3500", 2023,
                            VehicleCategory.TRUCK, "132.00", 41200, 70, "Chicago Hub", VehicleStatus.AVAILABLE),
                    build("DE1VIN00000000006", "CO-DE-401", "Rivian", "EDV 700", 2025,
                            VehicleCategory.EV, "125.00", 9800, 88, "Denver Hub", VehicleStatus.AVAILABLE),
                    build("DE1VIN00000000007", "CO-DE-402", "Subaru", "Outback AWD", 2024,
                            VehicleCategory.SUV, "82.00", 18400, 65, "Denver Hub", VehicleStatus.MAINTENANCE),
                    build("DE1VIN00000000008", "IL-DE-302", "Honda", "Accord Touring", 2024,
                            VehicleCategory.SEDAN, "72.00", 11900, 95, "Chicago Hub", VehicleStatus.AVAILABLE)
            ));
        };
    }

    private Vehicle build(String vin, String reg, String make, String model, int year,
                          VehicleCategory cat, String rate, int mileage, int fuel,
                          String hub, VehicleStatus status) {
        Vehicle v = new Vehicle();
        v.setVin(vin);
        v.setRegistrationNumber(reg);
        v.setMake(make);
        v.setModel(model);
        v.setModelYear(year);
        v.setCategory(cat);
        v.setDailyRate(new BigDecimal(rate));
        v.setMileage(mileage);
        v.setFuelLevelPercent(fuel);
        v.setLocationHub(hub);
        v.setStatus(status);
        v.setLastMaintenanceDate(LocalDate.now().minusDays(21));
        return v;
    }
}
