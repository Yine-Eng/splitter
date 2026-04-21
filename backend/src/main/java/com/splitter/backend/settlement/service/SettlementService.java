package com.splitter.backend.settlement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.splitter.backend.balance.dto.BalanceResponse;
import com.splitter.backend.balance.dto.UserBalanceDto;
import com.splitter.backend.balance.service.BalanceService;
import com.splitter.backend.group.repository.GroupMemberRepository;
import com.splitter.backend.models.User;
import com.splitter.backend.repository.UserRepository;
import com.splitter.backend.settlement.dto.CreateSettlementRequest;
import com.splitter.backend.settlement.dto.SettlementResponse;
import com.splitter.backend.settlement.model.Settlement;
import com.splitter.backend.settlement.model.SettlementStatus;
import com.splitter.backend.settlement.repository.SettlementRepository;

@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final BalanceService balanceService;

    public SettlementService(
            SettlementRepository settlementRepository,
            UserRepository userRepository,
            GroupMemberRepository groupMemberRepository,
            BalanceService balanceService
    ) {
        this.settlementRepository = settlementRepository;
        this.userRepository = userRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.balanceService = balanceService;
    }

    public SettlementResponse createSettlement(String requesterUsername, CreateSettlementRequest request) {
        User fromUser = userRepository.findByUsername(requesterUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

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

        boolean requesterInGroup = groupMemberRepository
                .findByGroupIdAndUserId(request.groupId(), fromUser.getId())
                .isPresent();

        boolean recipientInGroup = groupMemberRepository
                .findByGroupIdAndUserId(request.groupId(), request.toUserId())
                .isPresent();

        if (!requesterInGroup || !recipientInGroup) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Both users must be members of the group");
        }

        BigDecimal normalizedAmount = request.amount().setScale(2, RoundingMode.HALF_UP);

        BalanceResponse balanceResponse = balanceService.getGroupBalances(request.groupId(), requesterUsername);

        BigDecimal currentDebt = balanceResponse.getBalances().stream()
                .filter(balance ->
                        balance.getFromUserId().equals(fromUser.getId())
                                && balance.getToUserId().equals(request.toUserId()))
                .map(UserBalanceDto::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        if (currentDebt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You do not owe this user in the group");
        }

        if (normalizedAmount.compareTo(currentDebt) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Settlement amount exceeds current outstanding debt");
        }

        Settlement settlement = new Settlement(
                request.groupId(),
                fromUser.getId(),
                request.toUserId(),
                normalizedAmount,
                request.note() == null ? null : request.note().trim()
        );

        Settlement saved = settlementRepository.save(settlement);
        return toResponse(saved);
    }

    public SettlementResponse confirmSettlement(UUID settlementId, String confirmerUsername) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Settlement not found"));

        User confirmer = userRepository.findByUsername(confirmerUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        if (!settlement.getToUserId().equals(confirmer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient can confirm this settlement");
        }

        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending settlements can be confirmed");
        }

        settlement.setStatus(SettlementStatus.CONFIRMED);
        settlement.setConfirmedAt(LocalDateTime.now());

        Settlement saved = settlementRepository.save(settlement);
        return toResponse(saved);
    }

    public SettlementResponse rejectSettlement(UUID settlementId, String rejectorUsername, String reason) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Settlement not found"));

        User rejector = userRepository.findByUsername(rejectorUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

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
        return toResponse(saved);
    }

    public List<SettlementResponse> getIncomingPendingSettlements(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        return settlementRepository
                .findByToUserIdAndStatusOrderByCreatedAtDesc(user.getId(), SettlementStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<SettlementResponse> getOutgoingPendingSettlements(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        return settlementRepository
                .findByFromUserIdAndStatusOrderByCreatedAtDesc(user.getId(), SettlementStatus.PENDING)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<SettlementResponse> getGroupSettlementHistory(UUID groupId, String username) {
        User requester = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        boolean isMember = groupMemberRepository
                .findByGroupIdAndUserId(groupId, requester.getId())
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
                settlement.getRejectedAt()
        );
    }
}
