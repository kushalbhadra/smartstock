package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.ApiResponse;
import com.smartstock.smartstock.dto.UserProfileRequest;
import com.smartstock.smartstock.dto.UserRequest;
import com.smartstock.smartstock.dto.UserResponse;
import com.smartstock.smartstock.service.UserService;
import com.smartstock.smartstock.service.AuditEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuditEventService auditEventService;

    public UserController(
            UserService userService,
            AuditEventService auditEventService) {
        this.userService = userService;
        this.auditEventService = auditEventService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                userService.getCurrentProfile(userDetails.getUsername()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserProfileRequest request) {
        UserResponse response = userService.updateCurrentProfile(
                userDetails.getUsername(), request);
        auditEventService.record(response.getEmail(), response.getName(),
                "UPDATED", "PROFILE", response.getId(),
                "Updated personal profile and notification preferences");
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAll() {
        return ResponseEntity.ok(userService.getAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody UserRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserResponse response = userService.create(request);
        auditEventService.record(userDetails.getUsername(),
                userDetails.getUsername(), "CREATED", "USER",
                response.getId(), "Created user " + response.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UserRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserResponse response = userService.update(id, request);
        auditEventService.record(userDetails.getUsername(),
                userDetails.getUsername(), "UPDATED", "USER",
                response.getId(), "Updated user " + response.getEmail());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserResponse target = userService.getById(id);
        userService.delete(id, userDetails.getUsername());
        auditEventService.record(userDetails.getUsername(),
                userDetails.getUsername(), "DELETED", "USER",
                id, "Deleted user " + target.getEmail());
        return ResponseEntity.ok(ApiResponse.success("User deleted"));
    }
}
