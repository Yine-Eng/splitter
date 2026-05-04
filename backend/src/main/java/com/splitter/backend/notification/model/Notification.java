package com.splitter.backend.notification.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_notification_debt_reminder_per_day",
                        columnNames = {"group_id", "sender_user_id", "recipient_user_id", "type", "reminder_date"})
        },
        indexes = {
                @Index(
                        name = "idx_notification_rate_limit",
                        columnList = "group_id, sender_user_id, recipient_user_id, type, reminder_date")
        })
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID groupId;

    @Column(nullable = false)
    private Long senderUserId;

    @Column(nullable = false)
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    private UUID settlementId;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 1000)
    private String message;

    @Column(nullable = false)
    private boolean readByRecipient;

    @Column(nullable = false)
    private LocalDate reminderDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime readAt;

    public Notification() {
    }

    public Notification(
            UUID groupId,
            Long senderUserId,
            Long recipientUserId,
            NotificationType type,
            UUID settlementId,
            BigDecimal amount,
            String message) {
        this.groupId = groupId;
        this.senderUserId = senderUserId;
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.settlementId = settlementId;
        this.amount = amount;
        this.message = message;
        this.readByRecipient = false;
        this.reminderDate = LocalDate.now();
        this.createdAt = LocalDateTime.now();
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

    public LocalDate getReminderDate() {
        return reminderDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void markRead() {
        this.readByRecipient = true;
        this.readAt = LocalDateTime.now();
    }
}
