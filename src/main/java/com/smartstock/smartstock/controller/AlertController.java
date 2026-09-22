package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.entity.AlertLog;
import com.smartstock.smartstock.repository.AlertLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    @Autowired
    private AlertLogRepository alertLogRepository;

    // GET /api/alerts
    @GetMapping
    public ResponseEntity<List<AlertLog>> getAlerts() {
        return ResponseEntity.ok(
                alertLogRepository.findTop20ByOrderBySentAtDesc()
        );
    }

    // GET /api/alerts/failed
    @GetMapping("/failed")
    public ResponseEntity<List<AlertLog>> getFailedAlerts() {
        return ResponseEntity.ok(
                alertLogRepository.findByStatusOrderBySentAtDesc(
                        AlertLog.AlertStatus.FAILED
                )
        );
    }
}