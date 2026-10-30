package com.smartstock.smartstock.service;

import com.smartstock.smartstock.dto.AlertLogResponse;
import com.smartstock.smartstock.entity.AlertLog;
import com.smartstock.smartstock.repository.AlertLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AlertService {

    private final AlertLogRepository alertLogRepository;

    public AlertService(AlertLogRepository alertLogRepository) {
        this.alertLogRepository = alertLogRepository;
    }

    @Transactional(readOnly = true)
    public List<AlertLogResponse> getRecentAlerts() {
        return alertLogRepository.findTop20ByOrderBySentAtDesc()
                .stream()
                .map(AlertLogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertLogResponse> getFailedAlerts() {
        return alertLogRepository.findByStatusOrderBySentAtDesc(
                        AlertLog.AlertStatus.FAILED)
                .stream()
                .map(AlertLogResponse::from)
                .toList();
    }
}
