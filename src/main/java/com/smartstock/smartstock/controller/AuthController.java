package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.LoginRequest;
import com.smartstock.smartstock.dto.LoginResponse;
import com.smartstock.smartstock.entity.User;
import com.smartstock.smartstock.repository.UserRepository;
import com.smartstock.smartstock.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        // Step 1: Authenticate email + password
        // This calls UserDetailsService + PasswordEncoder internally
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Step 2: Load full user from database
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        // Step 3: Generate JWT token
        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRole().name(),
                user.getId(),
                user.getWarehouse() != null ? user.getWarehouse().getId() : null
        );

        // Step 4: Return token + user info to React
        return ResponseEntity.ok(LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .role(user.getRole().name())
                .warehouseId(user.getWarehouse() != null ?
                        user.getWarehouse().getId() : null)
                .name(user.getName())
                .phone(user.getPhone())
                .build()
        );
    }
}