package com.splitter.backend.notification.controller;

import com.splitter.backend.notification.dto.DebtReminderRequest;
import com.splitter.backend.notification.dto.NotificationResponse;
import com.splitter.backend.notification.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/api/groups/{groupId}/reminders/debt")
    public NotificationResponse sendDebtReminder(
            @PathVariable UUID groupId,
            @RequestBody DebtReminderRequest request,
            Authentication authentication) {
        return notificationService.sendDebtReminder(
                groupId,
                authentication.getName(),
                request.recipientUserId());
    }

    @PostMapping("/api/settlements/{settlementId}/remind")
    public NotificationResponse remindSettlementRecipient(
            @PathVariable UUID settlementId,
            Authentication authentication) {
        return notificationService.remindSettlementRecipient(
                settlementId,
                authentication.getName());
    }

    @GetMapping("/api/notifications")
    public List<NotificationResponse> getMyNotifications(Authentication authentication) {
        return notificationService.getMyNotifications(authentication.getName());
    }

    @PostMapping("/api/notifications/{notificationId}/read")
    public NotificationResponse markNotificationRead(
            @PathVariable UUID notificationId,
            Authentication authentication) {
        return notificationService.markNotificationRead(notificationId, authentication.getName());
    }
}
