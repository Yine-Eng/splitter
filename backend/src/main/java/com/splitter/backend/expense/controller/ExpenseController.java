package com.splitter.backend.expense.controller;

import com.splitter.backend.expense.model.Expense;
import com.splitter.backend.expense.service.ExpenseService;
import com.splitter.backend.models.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
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
    public Expense createExpense(@RequestBody CreateExpenseRequest request, Authentication authentication) {

        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }

        return expenseService.createExpense(
                request.groupId(),
                request.description(),
                request.amount(),
                user.getId(),
                request.participants());
    }

    public record CreateExpenseRequest(
            UUID groupId,
            String description,
            BigDecimal amount,
            Long paidByUserId,
            List<Long> participants) {
    }
}