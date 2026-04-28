package com.splitter.backend.group.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;

    public GroupService(
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            ExpenseRepository expenseRepository
    ) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
    }

    public Group createGroup(String name, String creatorUsername) {
        User creator = getUserByUsername(creatorUsername);

        Group group = new Group(name, creator.getId());
        Group savedGroup = groupRepository.save(group);

        GroupMember creatorMembership =
                new GroupMember(savedGroup.getId(), creator.getId(), GroupRole.ADMIN);

        groupMemberRepository.save(creatorMembership);

        return savedGroup;
    }

    public GroupMemberResponse addMember(UUID groupId, String requesterUsername, String targetUsername) {
        User requester = getUserByUsername(requesterUsername);

        if (targetUsername == null || targetUsername.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username or email is required");
        }

        User targetUser = userRepository.findByUsername(targetUsername.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user does not exist"));

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        GroupMember requesterMembership = groupMemberRepository
                .findByGroupIdAndUserId(group.getId(), requester.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group"));

        if (requesterMembership.getRole() != GroupRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can add members");
        }

        // check for duplicate after confirming requester has permission
        boolean alreadyMember = groupMemberRepository
                .findByGroupIdAndUserId(group.getId(), targetUser.getId())
                .isPresent();

        if (alreadyMember) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User is already in the group");
        }

        GroupMember newMember = new GroupMember(group.getId(), targetUser.getId(), GroupRole.MEMBER);
        GroupMember savedMember = groupMemberRepository.save(newMember);

        return new GroupMemberResponse(
                targetUser.getId(),
                targetUser.getUsername(),
                savedMember.getRole(),
                savedMember.getJoinedAt()
        );
    }

    public List<GroupMemberResponse> getGroupMembers(UUID groupId, String requesterUsername) {
        User requester = getUserByUsername(requesterUsername);

        ensureUserIsGroupMember(groupId, requester.getId());

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        List<GroupMemberResponse> responses = new ArrayList<>();

        for (GroupMember member : members) {
            User user = userRepository.findById(member.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group member user not found"));

            responses.add(new GroupMemberResponse(
                    user.getId(),
                    user.getUsername(),
                    member.getRole(),
                    member.getJoinedAt()
            ));
        }

        responses.sort(Comparator.comparing(GroupMemberResponse::getJoinedAt));

        return responses;
    }

    public List<GroupSummaryResponse> getGroupsForUser(String username) {
        User user = getUserByUsername(username);

        List<GroupMember> memberships = groupMemberRepository.findByUserId(user.getId());
        List<GroupSummaryResponse> responses = new ArrayList<>();

        for (GroupMember membership : memberships) {
            Group group = groupRepository.findById(membership.getGroupId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

            responses.add(new GroupSummaryResponse(
                    group.getId(),
                    group.getName(),
                    group.getCreatedByUserId(),
                    group.getCreatedAt(),
                    membership.getRole()
            ));
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
                    expense.getCreatedAt()
            ));
        }

        return responses;
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));
    }

    private void ensureUserIsGroupMember(UUID groupId, Long userId) {
        boolean isMember = groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .isPresent();

        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }
    }
}
