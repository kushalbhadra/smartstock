package com.smartstock.smartstock.dto;

import com.smartstock.smartstock.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Notification.Type type,
        String title,
        String message,
        Notification.Severity severity,
        boolean active,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        LocalDateTime lastOccurredAt,
        LocalDateTime resolvedAt,
        Notification.TargetType targetType,
        Long targetId
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getSeverity(),
                Boolean.TRUE.equals(notification.getActive()),
                notification.getReadAt(),
                notification.getCreatedAt(),
                notification.getLastOccurredAt(),
                notification.getResolvedAt(),
                notification.getTargetType(),
                notification.getTargetId()
        );
    }
}
