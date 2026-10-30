package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.AlertLog;

import java.time.LocalDateTime;

public record AlertLogResponse(
        Long id,
        UserSummary user,
        AlertLog.AlertType alertType,
        String message,
        AlertLog.AlertStatus status,
        Long relatedEntityId,
        LocalDateTime sentAt
) {
    public static AlertLogResponse from(AlertLog alert) {
        return new AlertLogResponse(
                alert.getId(),
                new UserSummary(alert.getUser().getId(), alert.getUser().getName()),
                alert.getAlertType(),
                alert.getMessage(),
                alert.getStatus(),
                alert.getRelatedEntityId(),
                alert.getSentAt()
        );
    }

    public record UserSummary(Long id, String name) {
    }
}
