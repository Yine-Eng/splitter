package com.splitter.backend.notification.dto;

import com.splitter.backend.notification.model.NotificationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class NotificationResponse {

    private UUID id;
    private UUID groupId;
    private Long senderUserId;
    private Long recipientUserId;
    private NotificationType type;
    private UUID settlementId;
    private BigDecimal amount;
    private String message;
    private boolean readByRecipient;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    public NotificationResponse(
            UUID id,
            UUID groupId,
            Long senderUserId,
            Long recipientUserId,
            NotificationType type,
            UUID settlementId,
            BigDecimal amount,
            String message,
            boolean readByRecipient,
            LocalDateTime createdAt,
            LocalDateTime readAt) {
        this.id = id;
        this.groupId = groupId;
        this.senderUserId = senderUserId;
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.settlementId = settlementId;
        this.amount = amount;
        this.message = message;
        this.readByRecipient = readByRecipient;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public NotificationType getType() {
        return type;
    }

    public UUID getSettlementId() {
        return settlementId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMessage() {
        return message;
    }

    public boolean isReadByRecipient() {
        return readByRecipient;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }
}
