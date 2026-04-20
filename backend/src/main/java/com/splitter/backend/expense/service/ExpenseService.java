package com.splitter.backend.expense.service;

import com.splitter.backend.expense.model.Expense;
import com.splitter.backend.expense.model.ExpenseSplit;
import com.splitter.backend.expense.repository.ExpenseRepository;
import com.splitter.backend.expense.repository.ExpenseSplitRepository;
import com.splitter.backend.group.model.GroupMember;
import com.splitter.backend.group.repository.GroupMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupMemberRepository groupMemberRepository;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpenseSplitRepository expenseSplitRepository,
            GroupMemberRepository groupMemberRepository
    ) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    public Expense createExpense(
            UUID groupId,
            String description,
            double amount,
            Long paidByUserId,
            List<Long> participants
    ) {

        if (groupId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group id is required");
        }

        if (description == null || description.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required");
        }

        if (amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }

        if (participants == null || participants.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one participant is required");
        }

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

        Expense expense = new Expense(groupId, description.trim(), amount, paidByUserId);
        Expense savedExpense = expenseRepository.save(expense);

        double splitAmount = amount / participants.size();

        for (Long userId : participants) {

            if (!userId.equals(paidByUserId)) {

                ExpenseSplit split =
                        new ExpenseSplit(savedExpense.getId(), userId, splitAmount);

                expenseSplitRepository.save(split);
            }
        }

        return savedExpense;
    }
}