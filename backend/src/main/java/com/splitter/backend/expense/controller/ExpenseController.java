package com.splitter.backend.expense.controller;

import com.splitter.backend.expense.model.Expense;
import com.splitter.backend.expense.service.ExpenseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public Expense createExpense(@RequestBody CreateExpenseRequest request) {

        return expenseService.createExpense(
                request.groupId(),
                request.description(),
                request.amount(),
                request.paidByUserId(),
                request.participants()
        );
    }

    public record CreateExpenseRequest(
            UUID groupId,
            String description,
            double amount,
            Long paidByUserId,
            List<Long> participants
    ) {}
}