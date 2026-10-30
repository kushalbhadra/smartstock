package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.SystemSettingsRequest;
import com.smartstock.smartstock.dto.SystemSettingsResponse;
import com.smartstock.smartstock.service.SystemSettingsService;
import com.smartstock.smartstock.service.AuditEventService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasRole('ADMIN')")
public class SystemSettingsController {

    private final SystemSettingsService settingsService;
    private final AuditEventService auditEventService;

    public SystemSettingsController(
            SystemSettingsService settingsService,
            AuditEventService auditEventService) {
        this.settingsService = settingsService;
        this.auditEventService = auditEventService;
    }

    @GetMapping
    public ResponseEntity<SystemSettingsResponse> get() {
        return ResponseEntity.ok(settingsService.get());
    }

    @PutMapping
    public ResponseEntity<SystemSettingsResponse> update(
            @Valid @RequestBody SystemSettingsRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        SystemSettingsResponse response = settingsService.update(request);
        auditEventService.record(userDetails.getUsername(),
                userDetails.getUsername(), "UPDATED", "SYSTEM_SETTINGS",
                null, "Updated company profile or inventory defaults");
        return ResponseEntity.ok(response);
    }
}
