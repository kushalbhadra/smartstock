package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.NotificationResponse;
import com.smartstock.smartstock.entity.Notification;
import com.smartstock.smartstock.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(
            @AuthenticationPrincipal UserDetails userDetails) {
        return Map.of("count", notificationService.getUnreadCount(
                userDetails.getUsername()));
    }

    @GetMapping("/recent")
    public List<NotificationResponse> getRecent(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "10") int limit) {
        return notificationService.getRecent(
                userDetails.getUsername(), limit);
    }

    @GetMapping
    public Page<NotificationResponse> getNotifications(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Notification.Type type,
            @RequestParam(required = false) Notification.Severity severity,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean unread,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.getNotifications(
                userDetails.getUsername(), type, severity, active, unread,
                page, size);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(
                userDetails.getUsername(), id));
    }

    @PatchMapping("/read-all")
    public Map<String, Integer> markAllAsRead(
            @AuthenticationPrincipal UserDetails userDetails) {
        return Map.of("updated", notificationService.markAllAsRead(
                userDetails.getUsername()));
    }
}
