package com.smartstock.smartstock.config;

import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminName;
    private final String adminEmail;
    private final String adminPassword;

    public DataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.name:}") String adminName,
            @Value("${app.bootstrap-admin.email:}") String adminEmail,
            @Value("${app.bootstrap-admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminName = adminName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        boolean hasAdminName = !adminName.isBlank();
        boolean hasAdminEmail = !adminEmail.isBlank();
        boolean hasAdminPassword = !adminPassword.isBlank();

        if (!(hasAdminName == hasAdminEmail
                && hasAdminEmail == hasAdminPassword)) {
            throw new IllegalStateException(
                    "Set SMARTSTOCK_BOOTSTRAP_ADMIN_NAME, "
                            + "SMARTSTOCK_BOOTSTRAP_ADMIN_EMAIL, and "
                            + "SMARTSTOCK_BOOTSTRAP_ADMIN_PASSWORD together");
        }

        if (userRepository.countByRole(User.Role.ADMIN) > 0) {
            return;
        }

        if (!hasAdminEmail) {
            throw new IllegalStateException(
                    "No administrator exists. Configure the "
                            + "SMARTSTOCK_BOOTSTRAP_ADMIN_* variables "
                            + "to provision the initial administrator");
        }

        if (adminPassword.length() < 12) {
            throw new IllegalStateException(
                    "SMARTSTOCK_BOOTSTRAP_ADMIN_PASSWORD must be at "
                            + "least 12 characters");
        }

        String normalizedEmail = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalStateException(
                    "The bootstrap administrator email is already used "
                            + "by a non-administrator account");
        }

        User admin = User.builder()
                .name(adminName.trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(adminPassword))
                .role(User.Role.ADMIN)
                .build();

        userRepository.save(admin);
        System.out.println("Initial administrator provisioned from environment");
    }
}