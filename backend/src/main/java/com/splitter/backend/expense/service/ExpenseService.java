package com.splitter.backend.expense.service;

import com.splitter.backend.expense.model.Expense;
import com.splitter.backend.expense.model.ExpenseSplit;
import com.splitter.backend.expense.repository.ExpenseRepository;
import com.splitter.backend.expense.repository.ExpenseSplitRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpenseSplitRepository expenseSplitRepository
    ) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
    }

    public Expense createExpense(
            UUID groupId,
            String description,
            double amount,
            Long paidByUserId,
            List<Long> participants
    ) {

        Expense expense = new Expense(groupId, description, amount, paidByUserId);
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