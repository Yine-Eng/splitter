package com.splitter.backend.expense.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.splitter.backend.expense.model.Expense;
import com.splitter.backend.expense.model.ExpenseSplit;
import com.splitter.backend.expense.repository.ExpenseRepository;
import com.splitter.backend.expense.repository.ExpenseSplitRepository;
import com.splitter.backend.group.model.Group;
import com.splitter.backend.group.model.GroupMember;
import com.splitter.backend.group.repository.GroupMemberRepository;
import com.splitter.backend.group.repository.GroupRepository;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRepository groupRepository;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpenseSplitRepository expenseSplitRepository,
            GroupMemberRepository groupMemberRepository,
            GroupRepository groupRepository
    ) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupRepository = groupRepository;
    }

    public Expense createExpense(
            UUID groupId,
            String description,
            BigDecimal amount,
            Long paidByUserId,
            List<Long> participants
    ) {

        if (groupId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group id is required");
        }

        if (description == null || description.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }

        if (participants == null || participants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one participant is required");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        Set<Long> memberIds = new HashSet<>();
        for (GroupMember member : members) {
            memberIds.add(member.getUserId());
        }

        if (!memberIds.contains(paidByUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }

        Set<Long> uniqueParticipants = new HashSet<>(participants);
        if (uniqueParticipants.size() != participants.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate participants are not allowed");
        }

        for (Long participantId : participants) {
            if (participantId == null || !memberIds.contains(participantId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "All participants must be members of the group"
                );
            }
        }

        BigDecimal normalizedAmount = amount.setScale(2);

        Expense expense = new Expense(groupId, description.trim(), normalizedAmount, paidByUserId);
        Expense savedExpense = expenseRepository.save(expense);

        long totalCents = normalizedAmount.movePointRight(2).longValueExact();
        int participantCount = participants.size();
        long baseCents = totalCents / participantCount;
        int remainderCents = (int) (totalCents % participantCount);

        int startIndex = group.getRemainderStartIndex() == null ? 0 : group.getRemainderStartIndex();
        startIndex = Math.floorMod(startIndex, participantCount);

        Map<Long, BigDecimal> shareByUserId = new HashMap<>();

        for (Long userId : participants) {
            shareByUserId.put(userId, BigDecimal.valueOf(baseCents, 2));
        }

        for (int i = 0; i < remainderCents; i++) {
            int participantIndex = (startIndex + i) % participantCount;
            Long userId = participants.get(participantIndex);

            BigDecimal updatedShare = shareByUserId.get(userId).add(BigDecimal.valueOf(0.01));
            shareByUserId.put(userId, updatedShare);
        }

        for (Long userId : participants) {
            if (!userId.equals(paidByUserId)) {
                ExpenseSplit split = new ExpenseSplit(
                        savedExpense.getId(),
                        userId,
                        shareByUserId.get(userId)
                );

                expenseSplitRepository.save(split);
            }
        }

        int nextStartIndex = (startIndex + remainderCents) % participantCount;
        group.setRemainderStartIndex(nextStartIndex);
        groupRepository.save(group);

        return savedExpense;
    }
}
