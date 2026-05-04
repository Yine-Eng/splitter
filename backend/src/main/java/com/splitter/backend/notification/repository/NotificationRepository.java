package com.splitter.backend.notification.repository;

import com.splitter.backend.notification.model.Notification;
import com.splitter.backend.notification.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);

    boolean existsByGroupIdAndSenderUserIdAndRecipientUserIdAndTypeAndReminderDate(
            UUID groupId,
            Long senderUserId,
            Long recipientUserId,
            NotificationType type,
            LocalDate reminderDate);

    boolean existsBySettlementIdAndSenderUserIdAndRecipientUserIdAndTypeAndReminderDate(
            UUID settlementId,
            Long senderUserId,
            Long recipientUserId,
            NotificationType type,
            LocalDate reminderDate);
}
