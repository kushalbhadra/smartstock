package com.smartstock.smartstock.config;

import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        // Create Admin if he doesn't already exist
        if (!userRepository.existsByEmail("admin@smartstock.com")) {

            User admin = User.builder()
                    .name("Rahul Sharma")
                    .email("admin@smartstock.com")
                    .password(passwordEncoder.encode("demo1234"))
                    .role(User.Role.ADMIN)
                    .phone("+91-9876543210")
                    .whatsappAlertsEnabled(true)
                    .build();

            userRepository.save(admin);

            System.out.println("Admin user created");
        }

        // Create Viewer if she doesn't already exist
        if (!userRepository.existsByEmail("sneha@smartstock.com")) {

            User viewer = User.builder()
                    .name("Sneha Verma")
                    .email("sneha@smartstock.com")
                    .password(passwordEncoder.encode("demo1234"))
                    .role(User.Role.VIEWER)
                    .phone("+91-9876543211")
                    .whatsappAlertsEnabled(false)
                    .build();

            userRepository.save(viewer);

            System.out.println("Viewer user created");
        }
    }
}