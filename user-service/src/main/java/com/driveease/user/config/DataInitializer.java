package com.driveease.user.config;

import com.driveease.user.entity.User;
import com.driveease.user.entity.UserRole;
import com.driveease.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedUsers(UserRepository userRepository, PasswordEncoder encoder) {
        return args -> {
            seedIfMissing(userRepository, encoder,
                    "DriveEase Operations Admin", "admin@driveease.com", "Admin@123",
                    "+1-206-555-0100", "DL-ADM-0001", UserRole.ADMIN);
            seedIfMissing(userRepository, encoder,
                    "Morgan Fleet Manager", "manager@driveease.com", "Manager@123",
                    "+1-206-555-0144", "DL-MGR-0241", UserRole.FLEET_MANAGER);
            seedIfMissing(userRepository, encoder,
                    "Alex Rivera", "alex@driveease.com", "Customer@123",
                    "+1-512-555-0188", "DL-TX-88412", UserRole.CUSTOMER);
        };
    }

    private void seedIfMissing(UserRepository repo, PasswordEncoder encoder,
                               String name, String email, String rawPass,
                               String phone, String license, UserRole role) {
        if (repo.existsByEmailIgnoreCase(email)) {
            return;
        }
        User user = new User();
        user.setFullName(name);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(rawPass));
        user.setPhone(phone);
        user.setDriverLicenseNo(license);
        user.setRole(role);
        user.setActive(true);
        repo.save(user);
    }
}
