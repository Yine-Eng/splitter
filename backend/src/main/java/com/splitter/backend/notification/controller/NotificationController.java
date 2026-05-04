package com.splitter.backend.notification.controller;

import com.splitter.backend.notification.dto.NotificationResponse;
import com.splitter.backend.notification.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> getMyNotifications(Authentication authentication) {
        return notificationService.getMyNotifications(authentication.getName());
    }

    @PostMapping("/{notificationId}/read")
    public NotificationResponse markNotificationRead(
            @PathVariable UUID notificationId,
            Authentication authentication) {
        return notificationService.markNotificationRead(notificationId, authentication.getName());
    }
}
