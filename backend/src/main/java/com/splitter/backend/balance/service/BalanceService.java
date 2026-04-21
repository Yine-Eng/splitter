package com.splitter.backend.balance.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.splitter.backend.balance.dto.BalanceResponse;
import com.splitter.backend.balance.dto.UserBalanceDto;
import com.splitter.backend.expense.model.Expense;
import com.splitter.backend.expense.model.ExpenseSplit;
import com.splitter.backend.expense.repository.ExpenseRepository;
import com.splitter.backend.expense.repository.ExpenseSplitRepository;
import com.splitter.backend.group.repository.GroupMemberRepository;
import com.splitter.backend.models.User;
import com.splitter.backend.repository.UserRepository;

@Service
public class BalanceService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public BalanceService(
            ExpenseRepository expenseRepository,
            ExpenseSplitRepository expenseSplitRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository
    ) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
    }

    public BalanceResponse getGroupBalances(UUID groupId, String requesterUsername) {

        User requester = userRepository.findByUsername(requesterUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        boolean isMember = groupMemberRepository
                .findByGroupIdAndUserId(groupId, requester.getId())
                .isPresent();

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not a member of this group"
            );
        }

        List<Expense> expenses = expenseRepository.findByGroupId(groupId);

        if (expenses.isEmpty()) {
            return new BalanceResponse(groupId, Collections.emptyList());
        }

        Map<UUID, Expense> expenseById = new HashMap<>();
        List<UUID> expenseIds = new ArrayList<>();

        for (Expense expense : expenses) {
            expenseById.put(expense.getId(), expense);
            expenseIds.add(expense.getId());
        }

        List<ExpenseSplit> allSplits = expenseSplitRepository.findByExpenseIdIn(expenseIds);

        Map<Long, Map<Long, BigDecimal>> rawBalances = new HashMap<>();

        for (ExpenseSplit split : allSplits) {
            if (split.isPaid()) {
                continue;
            }

            Expense expense = expenseById.get(split.getExpenseId());
            if (expense == null) {
                continue;
            }

            Long debtorId = split.getUserId();
            Long creditorId = expense.getPaidByUserId();
            BigDecimal amount = split.getAmountOwed();

            if (debtorId.equals(creditorId) || amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            rawBalances
                    .computeIfAbsent(debtorId, ignored -> new HashMap<>())
                    .merge(creditorId, amount, BigDecimal::add);
        }

        List<UserBalanceDto> simplifiedBalances = simplifyBalances(rawBalances);

        return new BalanceResponse(groupId, simplifiedBalances);
    }

    private List<UserBalanceDto> simplifyBalances(Map<Long, Map<Long, BigDecimal>> rawBalances) {
        List<UserBalanceDto> result = new ArrayList<>();
        Set<String> visitedPairs = new HashSet<>();

        for (Map.Entry<Long, Map<Long, BigDecimal>> debtorEntry : rawBalances.entrySet()) {
            Long fromUser = debtorEntry.getKey();

            for (Map.Entry<Long, BigDecimal> creditorEntry : debtorEntry.getValue().entrySet()) {
                Long toUser = creditorEntry.getKey();
                BigDecimal forwardAmount = creditorEntry.getValue();

                if (forwardAmount.compareTo(BigDecimal.ZERO) <= 0 || fromUser.equals(toUser)) {
                    continue;
                }

                String pairKey = fromUser + "-" + toUser;
                String reverseKey = toUser + "-" + fromUser;

                if (visitedPairs.contains(pairKey) || visitedPairs.contains(reverseKey)) {
                    continue;
                }

                BigDecimal reverseAmount = rawBalances
                        .getOrDefault(toUser, Collections.emptyMap())
                        .getOrDefault(fromUser, BigDecimal.ZERO);

                BigDecimal net = forwardAmount.subtract(reverseAmount);

                if (net.compareTo(BigDecimal.ZERO) > 0) {
                    result.add(new UserBalanceDto(fromUser, toUser, net));
                } else if (net.compareTo(BigDecimal.ZERO) < 0) {
                    result.add(new UserBalanceDto(toUser, fromUser, net.abs()));
                }

                visitedPairs.add(pairKey);
                visitedPairs.add(reverseKey);
            }
        }

        return result;
    }
}