package com.splitter.backend.group.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.splitter.backend.expense.model.Expense;
import com.splitter.backend.expense.repository.ExpenseRepository;
import com.splitter.backend.group.dto.GroupExpenseResponse;
import com.splitter.backend.group.dto.GroupMemberResponse;
import com.splitter.backend.group.dto.GroupSummaryResponse;
import com.splitter.backend.group.model.Group;
import com.splitter.backend.group.model.GroupMember;
import com.splitter.backend.group.model.GroupRole;
import com.splitter.backend.group.repository.GroupMemberRepository;
import com.splitter.backend.group.repository.GroupRepository;
import com.splitter.backend.models.User;
import com.splitter.backend.repository.UserRepository;
import com.splitter.backend.event.model.GroupEventType;
import com.splitter.backend.event.model.GroupEventVisibility;
import com.splitter.backend.event.service.GroupEventService;
import com.splitter.backend.balance.dto.BalanceResponse;
import com.splitter.backend.balance.dto.UserBalanceDto;
import com.splitter.backend.balance.service.BalanceService;
import com.splitter.backend.group.dto.GroupMemberRemovalPreviewResponse;
import com.splitter.backend.group.dto.RemovalBalanceItem;
import com.splitter.backend.settlement.model.Settlement;
import com.splitter.backend.settlement.model.SettlementStatus;
import com.splitter.backend.settlement.repository.SettlementRepository;

import java.math.BigDecimal;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final GroupEventService groupEventService;
    private final BalanceService balanceService;
    private final SettlementRepository settlementRepository;

    public GroupService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            GroupEventService groupEventService,
            BalanceService balanceService,
            SettlementRepository settlementRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.groupEventService = groupEventService;
        this.balanceService = balanceService;
        this.settlementRepository = settlementRepository;
    }

    public Group createGroup(String name, String creatorUsername) {
        User creator = getUserByUsername(creatorUsername);

        Group group = new Group(name, creator.getId());
        Group savedGroup = groupRepository.save(group);

        GroupMember creatorMembership = new GroupMember(savedGroup.getId(), creator.getId(), GroupRole.ADMIN);

        groupMemberRepository.save(creatorMembership);

        return savedGroup;
    }

    @Transactional
    public GroupMemberResponse addMember(UUID groupId, String requesterUsername, String targetUsername) {
        User requester = getUserByUsername(requesterUsername);

        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username or email is required");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        GroupMember requesterMembership = groupMemberRepository
                .findByGroupIdAndUserIdAndActiveTrue(group.getId(), requester.getId())
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group"));

        if (requesterMembership.getRole() != GroupRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can add members");
        }

        User targetUser = userRepository.findByUsername(targetUsername.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user does not exist"));

        boolean alreadyMember = groupMemberRepository
                .findByGroupIdAndUserIdAndActiveTrue(group.getId(), targetUser.getId())
                .isPresent();

        if (alreadyMember) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already in the group");
        }

        GroupMember newMember = new GroupMember(group.getId(), targetUser.getId(), GroupRole.MEMBER);
        GroupMember savedMember = groupMemberRepository.save(newMember);

        groupEventService.createGroupEvent(
                group.getId(),
                GroupEventType.MEMBER_ADDED,
                GroupEventVisibility.GROUP,
                requester.getId(),
                targetUser.getId(),
                null,
                "Member added to group");

        return new GroupMemberResponse(
                targetUser.getId(),
                targetUser.getUsername(),
                savedMember.getRole(),
                savedMember.getJoinedAt());
    }

    public List<GroupMemberResponse> getGroupMembers(UUID groupId, String requesterUsername) {
        User requester = getUserByUsername(requesterUsername);

        ensureUserIsGroupMember(groupId, requester.getId());

        List<GroupMember> members = groupMemberRepository.findByGroupIdAndActiveTrue(groupId);

        List<Long> userIds = members.stream()
                .map(GroupMember::getUserId)
                .toList();

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<GroupMemberResponse> responses = new ArrayList<>();

        for (GroupMember member : members) {
            User user = userMap.get(member.getUserId());
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group member user not found");
            }

            responses.add(new GroupMemberResponse(
                    user.getId(),
                    user.getUsername(),
                    member.getRole(),
                    member.getJoinedAt()));
        }

        responses.sort(Comparator.comparing(GroupMemberResponse::getJoinedAt));

        return responses;
    }

    public List<GroupSummaryResponse> getGroupsForUser(String username) {
        User user = getUserByUsername(username);

        List<GroupMember> memberships = groupMemberRepository.findByUserIdAndActiveTrue(user.getId());

        List<UUID> groupIds = memberships.stream()
                .map(GroupMember::getGroupId)
                .toList();

        Map<UUID, Group> groupMap = groupRepository.findByIdIn(groupIds).stream()
                .collect(Collectors.toMap(Group::getId, Function.identity()));

        List<GroupSummaryResponse> responses = new ArrayList<>();

        for (GroupMember membership : memberships) {
            Group group = groupMap.get(membership.getGroupId());
            if (group == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
            }

            responses.add(new GroupSummaryResponse(
                    group.getId(),
                    group.getName(),
                    group.getCreatedByUserId(),
                    group.getCreatedAt(),
                    membership.getRole()));
        }

        responses.sort(Comparator.comparing(GroupSummaryResponse::getCreatedAt).reversed());

        return responses;
    }

    public List<GroupExpenseResponse> getGroupExpenses(UUID groupId, String requesterUsername) {
        User requester = getUserByUsername(requesterUsername);

        ensureUserIsGroupMember(groupId, requester.getId());

        List<Expense> expenses = expenseRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
        List<GroupExpenseResponse> responses = new ArrayList<>();

        for (Expense expense : expenses) {
            responses.add(new GroupExpenseResponse(
                    expense.getId(),
                    expense.getGroupId(),
                    expense.getDescription(),
                    expense.getAmount(),
                    expense.getPaidByUserId(),
                    expense.getCreatedAt()));
        }

        return responses;
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private void ensureUserIsGroupMember(UUID groupId, Long userId) {
        boolean isMember = groupMemberRepository
                .findByGroupIdAndUserIdAndActiveTrue(groupId, userId)
                .isPresent();

        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }
    }

    public GroupMemberRemovalPreviewResponse previewMemberRemoval(
            UUID groupId,
            Long targetUserId,
            String requesterUsername) {
        User requester = getUserByUsername(requesterUsername);
        ensureUserIsGroupAdmin(groupId, requester.getId());

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user does not exist"));

        groupMemberRepository.findByGroupIdAndUserIdAndActiveTrue(groupId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User is not an active member of this group"));

        return buildRemovalPreview(groupId, targetUser);
    }

    @Transactional
    public GroupMemberRemovalPreviewResponse removeMember(
            UUID groupId,
            Long targetUserId,
            String requesterUsername) {
        User requester = getUserByUsername(requesterUsername);
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        ensureUserIsGroupAdmin(groupId, requester.getId());

        if (requester.getId().equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Admins cannot remove themselves using this endpoint");
        }

        if (group.getCreatedByUserId().equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Group creator cannot be removed in this version");
        }

        GroupMember targetMembership = groupMemberRepository
                .findByGroupIdAndUserIdAndActiveTrue(groupId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User is not an active member of this group"));

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user does not exist"));

        GroupMemberRemovalPreviewResponse preview = buildRemovalPreview(groupId, targetUser);

        if (preview.getPendingSettlementCount() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This member has pending settlements. Resolve them before removal.");
        }

        boolean hasOutstandingBalances = !preview.getUserOwes().isEmpty() || !preview.getUserIsOwed().isEmpty();

        if (hasOutstandingBalances) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This member has outstanding balances. Settle all balances before removal.");
        }

        targetMembership.markRemoved();
        groupMemberRepository.save(targetMembership);

        groupEventService.createGroupEvent(
                groupId,
                GroupEventType.MEMBER_REMOVED,
                GroupEventVisibility.GROUP,
                requester.getId(),
                targetUserId,
                null,
                "Member removed from group");

        return preview;
    }

    private GroupMemberRemovalPreviewResponse buildRemovalPreview(UUID groupId, User targetUser) {
        BalanceResponse balanceResponse = balanceService.getGroupBalances(groupId, targetUser.getUsername());

        List<RemovalBalanceItem> userOwes = new ArrayList<>();
        List<RemovalBalanceItem> userIsOwed = new ArrayList<>();

        for (UserBalanceDto balance : balanceResponse.getBalances()) {
            if (balance.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            if (balance.getFromUserId().equals(targetUser.getId())) {
                userOwes.add(new RemovalBalanceItem(
                        balance.getFromUserId(),
                        balance.getToUserId(),
                        balance.getAmount()));
            }

            if (balance.getToUserId().equals(targetUser.getId())) {
                userIsOwed.add(new RemovalBalanceItem(
                        balance.getFromUserId(),
                        balance.getToUserId(),
                        balance.getAmount()));
            }
        }

        List<Settlement> pendingSettlements = settlementRepository.findByGroupIdAndStatus(groupId,
                SettlementStatus.PENDING);

        int pendingCount = 0;
        for (Settlement settlement : pendingSettlements) {
            if (settlement.getFromUserId().equals(targetUser.getId())
                    || settlement.getToUserId().equals(targetUser.getId())) {
                pendingCount++;
            }
        }

        boolean canRemoveWithoutConfirmation = userOwes.isEmpty() && userIsOwed.isEmpty() && pendingCount == 0;

        return new GroupMemberRemovalPreviewResponse(
                groupId,
                targetUser.getId(),
                targetUser.getUsername(),
                userOwes,
                userIsOwed,
                pendingCount,
                canRemoveWithoutConfirmation);
    }

    private void ensureUserIsGroupAdmin(UUID groupId, Long userId) {
        GroupMember membership = groupMemberRepository
                .findByGroupIdAndUserIdAndActiveTrue(groupId, userId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group"));

        if (membership.getRole() != GroupRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can perform this action");
        }
    }
}
