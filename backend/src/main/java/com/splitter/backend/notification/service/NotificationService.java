package com.splitter.backend.notification.service;

import com.splitter.backend.balance.dto.BalanceResponse;
import com.splitter.backend.balance.dto.UserBalanceDto;
import com.splitter.backend.balance.service.BalanceService;
import com.splitter.backend.group.repository.GroupMemberRepository;
import com.splitter.backend.models.User;
import com.splitter.backend.notification.dto.NotificationResponse;
import com.splitter.backend.notification.model.Notification;
import com.splitter.backend.notification.model.NotificationType;
import com.splitter.backend.notification.repository.NotificationRepository;
import com.splitter.backend.repository.UserRepository;
import com.splitter.backend.settlement.model.Settlement;
import com.splitter.backend.settlement.model.SettlementStatus;
import com.splitter.backend.settlement.repository.SettlementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final BalanceService balanceService;
    private final SettlementRepository settlementRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            GroupMemberRepository groupMemberRepository,
            BalanceService balanceService,
            SettlementRepository settlementRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.balanceService = balanceService;
        this.settlementRepository = settlementRepository;
    }

    public NotificationResponse sendDebtReminder(
            UUID groupId,
            String requesterUsername,
            Long recipientUserId) {
        User requester = getUserByUsername(requesterUsername);

        if (recipientUserId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient user id is required");
        }

        if (requester.getId().equals(recipientUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot remind yourself");
        }

        ensureActiveGroupMember(groupId, requester.getId());
        ensureActiveGroupMember(groupId, recipientUserId);

        BalanceResponse balanceResponse = balanceService.getGroupBalances(groupId, requesterUsername);

        BigDecimal amountOwed = balanceResponse.getBalances().stream()
                .filter(balance -> balance.getFromUserId().equals(recipientUserId)
                        && balance.getToUserId().equals(requester.getId()))
                .map(UserBalanceDto::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        if (amountOwed.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This user does not currently owe you in this group");
        }

        boolean alreadySentToday = notificationRepository
                .existsByGroupIdAndSenderUserIdAndRecipientUserIdAndTypeAndReminderDate(
                        groupId,
                        requester.getId(),
                        recipientUserId,
                        NotificationType.DEBT_REMINDER,
                        LocalDate.now());

        if (alreadySentToday) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "You already sent this reminder today");
        }

        Notification notification = new Notification(
                groupId,
                requester.getId(),
                recipientUserId,
                NotificationType.DEBT_REMINDER,
                null,
                amountOwed,
                "Debt reminder");

        Notification saved = notificationRepository.save(notification);
        return toResponse(saved);
    }

    public NotificationResponse remindSettlementRecipient(UUID settlementId, String requesterUsername) {
        User requester = getUserByUsername(requesterUsername);

        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Settlement not found"));

        if (!settlement.getFromUserId().equals(requester.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the settlement sender can send this reminder");
        }

        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending settlements can be reminded");
        }

        ensureActiveGroupMember(settlement.getGroupId(), settlement.getFromUserId());
        ensureActiveGroupMember(settlement.getGroupId(), settlement.getToUserId());

        boolean alreadySentToday = notificationRepository
                .existsBySettlementIdAndSenderUserIdAndRecipientUserIdAndTypeAndReminderDate(
                        settlement.getId(),
                        settlement.getFromUserId(),
                        settlement.getToUserId(),
                        NotificationType.SETTLEMENT_CONFIRMATION_REMINDER,
                        LocalDate.now());

        if (alreadySentToday) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "You already sent this settlement reminder today");
        }

        Notification notification = new Notification(
                settlement.getGroupId(),
                settlement.getFromUserId(),
                settlement.getToUserId(),
                NotificationType.SETTLEMENT_CONFIRMATION_REMINDER,
                settlement.getId(),
                settlement.getAmount(),
                "Settlement confirmation reminder");

        Notification saved = notificationRepository.save(notification);
        return toResponse(saved);
    }

    public List<NotificationResponse> getMyNotifications(String username) {
        User user = getUserByUsername(username);

        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public NotificationResponse markNotificationRead(UUID notificationId, String username) {
        User user = getUserByUsername(username);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        if (!notification.getRecipientUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot update this notification");
        }

        if (!notification.isReadByRecipient()) {
            notification.markRead();
            notification = notificationRepository.save(notification);
        }

        return toResponse(notification);
    }

    private void ensureActiveGroupMember(UUID groupId, Long userId) {
        boolean isActiveMember = groupMemberRepository
                .findByGroupIdAndUserIdAndActiveTrue(groupId, userId)
                .isPresent();

        if (!isActiveMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not an active member of this group");
        }
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getGroupId(),
                notification.getSenderUserId(),
                notification.getRecipientUserId(),
                notification.getType(),
                notification.getSettlementId(),
                notification.getAmount(),
                notification.getMessage(),
                notification.isReadByRecipient(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }
}
