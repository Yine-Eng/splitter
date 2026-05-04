package com.splitter.backend.settlement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.splitter.backend.balance.dto.BalanceResponse;
import com.splitter.backend.balance.dto.UserBalanceDto;
import com.splitter.backend.balance.service.BalanceService;
import com.splitter.backend.group.model.Group;
import com.splitter.backend.group.repository.GroupMemberRepository;
import com.splitter.backend.group.repository.GroupRepository;
import com.splitter.backend.models.User;
import com.splitter.backend.repository.UserRepository;
import com.splitter.backend.settlement.dto.CreateSettlementRequest;
import com.splitter.backend.settlement.dto.SettlementResponse;
import com.splitter.backend.settlement.model.Settlement;
import com.splitter.backend.settlement.model.SettlementStatus;
import com.splitter.backend.settlement.repository.SettlementRepository;
import com.splitter.backend.event.model.GroupEventType;
import com.splitter.backend.event.model.GroupEventVisibility;
import com.splitter.backend.event.service.GroupEventService;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final SettlementRepository settlementRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;
    private final BalanceService balanceService;
    private final GroupEventService groupEventService;

    public SettlementService(
            SettlementRepository settlementRepository,
            UserRepository userRepository,
            GroupMemberRepository groupMemberRepository,
            GroupRepository groupRepository,
            BalanceService balanceService,
            GroupEventService groupEventService) {
        this.settlementRepository = settlementRepository;
        this.userRepository = userRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupRepository = groupRepository;
        this.balanceService = balanceService;
        this.groupEventService = groupEventService;
    }

    @Transactional
    public SettlementResponse createSettlement(String requesterUsername, CreateSettlementRequest request) {
        User fromUser = userRepository.findByUsername(requesterUsername)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        if (request.groupId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group id is required");
        }

        if (request.toUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient is required");
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }

        if (fromUser.getId().equals(request.toUserId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot settle with yourself");
        }

        Group group = groupRepository.findById(request.groupId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        if (group.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Group is archived");
        }

        boolean requesterInGroup = groupMemberRepository
                .findByGroupIdAndUserIdAndActiveTrue(request.groupId(), fromUser.getId())
                .isPresent();

        boolean recipientInGroup = groupMemberRepository
                .findByGroupIdAndUserIdAndActiveTrue(request.groupId(), request.toUserId())
                .isPresent();

        if (!requesterInGroup || !recipientInGroup) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Both users must be members of the group");
        }

        BigDecimal normalizedAmount = request.amount().setScale(2, RoundingMode.HALF_UP);

        BalanceResponse balanceResponse = balanceService.getGroupBalances(request.groupId(), requesterUsername);

        BigDecimal currentDebt = balanceResponse.getBalances().stream()
                .filter(balance -> balance.getFromUserId().equals(fromUser.getId())
                        && balance.getToUserId().equals(request.toUserId()))
                .map(UserBalanceDto::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        if (currentDebt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You do not owe this user in the group");
        }

        if (normalizedAmount.compareTo(currentDebt) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Settlement amount exceeds current outstanding debt");
        }

        Settlement settlement = new Settlement(
                request.groupId(),
                fromUser.getId(),
                request.toUserId(),
                normalizedAmount,
                request.note() == null ? null : request.note().trim());

        Settlement saved = settlementRepository.save(settlement);
        groupEventService.createGroupEvent(
                saved.getGroupId(),
                GroupEventType.SETTLEMENT_CREATED,
                GroupEventVisibility.PRIVATE,
                saved.getFromUserId(),
                saved.getToUserId(),
                saved.getAmount(),
                "Settlement claim created");
        return toResponse(saved);
    }

    @Transactional
    public SettlementResponse confirmSettlement(UUID settlementId, String confirmerUsername) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Settlement not found"));

        User confirmer = userRepository.findByUsername(confirmerUsername)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        if (!settlement.getToUserId().equals(confirmer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient can confirm this settlement");
        }

        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending settlements can be confirmed");
        }

        BalanceResponse balanceResponse = balanceService.getGroupBalances(settlement.getGroupId(), confirmerUsername);

        BigDecimal currentDebt = balanceResponse.getBalances().stream()
                .filter(balance -> balance.getFromUserId().equals(settlement.getFromUserId())
                        && balance.getToUserId().equals(settlement.getToUserId()))
                .map(UserBalanceDto::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        if (currentDebt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No outstanding debt exists to confirm this settlement");
        }

        if (settlement.getAmount().compareTo(currentDebt) > 0) {
            log.info("Settlement {} amount {} exceeds current debt {}; adjusting to current debt",
                    settlementId, settlement.getAmount(), currentDebt);
            settlement.setAmount(currentDebt);
        }

        settlement.setStatus(SettlementStatus.CONFIRMED);
        settlement.setConfirmedAt(LocalDateTime.now());

        Settlement saved = settlementRepository.save(settlement);
        groupEventService.createGroupEvent(
                saved.getGroupId(),
                GroupEventType.SETTLEMENT_CONFIRMED,
                GroupEventVisibility.GROUP,
                saved.getToUserId(),
                saved.getFromUserId(),
                saved.getAmount(),
                "Settlement confirmed");
        return toResponse(saved);
    }

    @Transactional
    public SettlementResponse rejectSettlement(UUID settlementId, String rejectorUsername, String reason) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Settlement not found"));

        User rejector = userRepository.findByUsername(rejectorUsername)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        if (!settlement.getToUserId().equals(rejector.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient can reject this settlement");
        }

        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending settlements can be rejected");
        }

        settlement.setStatus(SettlementStatus.REJECTED);
        settlement.setRejectedAt(LocalDateTime.now());
        settlement.setRejectionReason(reason == null ? null : reason.trim());

        Settlement saved = settlementRepository.save(settlement);
        groupEventService.createGroupEvent(
                saved.getGroupId(),
                GroupEventType.SETTLEMENT_REJECTED,
                GroupEventVisibility.PRIVATE,
                saved.getToUserId(),
                saved.getFromUserId(),
                saved.getAmount(),
                "Settlement rejected");
        return toResponse(saved);
    }

    public List<SettlementResponse> getIncomingPendingSettlements(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        return settlementRepository
                .findByToUserIdAndStatusOrderByCreatedAtDesc(user.getId(), SettlementStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<SettlementResponse> getOutgoingPendingSettlements(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        return settlementRepository
                .findByFromUserIdAndStatusOrderByCreatedAtDesc(user.getId(), SettlementStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<SettlementResponse> getGroupSettlementHistory(UUID groupId, String username) {
        User requester = userRepository.findByUsername(username)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        boolean isMember = groupMemberRepository
                .findByGroupIdAndUserIdAndActiveTrue(groupId, requester.getId())
                .isPresent();

        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }

        return settlementRepository
                .findByGroupIdAndStatusOrderByCreatedAtDesc(groupId, SettlementStatus.CONFIRMED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private SettlementResponse toResponse(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getGroupId(),
                settlement.getFromUserId(),
                settlement.getToUserId(),
                settlement.getAmount(),
                settlement.getStatus(),
                settlement.getNote(),
                settlement.getRejectionReason(),
                settlement.getCreatedAt(),
                settlement.getConfirmedAt(),
                settlement.getRejectedAt());
    }
}
