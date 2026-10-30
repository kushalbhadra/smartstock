package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.AlertLogResponse;
import com.smartstock.smartstock.service.AlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@PreAuthorize("hasRole('ADMIN')")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    // GET /api/alerts
    @GetMapping
    public ResponseEntity<List<AlertLogResponse>> getAlerts() {
        return ResponseEntity.ok(alertService.getRecentAlerts());
    }

    // GET /api/alerts/failed
    @GetMapping("/failed")
    public ResponseEntity<List<AlertLogResponse>> getFailedAlerts() {
        return ResponseEntity.ok(alertService.getFailedAlerts());
    }
}