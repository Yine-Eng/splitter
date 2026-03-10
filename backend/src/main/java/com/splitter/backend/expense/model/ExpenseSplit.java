package com.splitter.backend.expense.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "expense_splits")
public class ExpenseSplit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID expenseId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private double amountOwed;

    @Column(nullable = false)
    private boolean paid;

    public ExpenseSplit() {}

    public ExpenseSplit(UUID expenseId, Long userId, double amountOwed) {
        this.expenseId = expenseId;
        this.userId = userId;
        this.amountOwed = amountOwed;
        this.paid = false;
    }

    public UUID getExpenseId() { return expenseId; }
    public Long getUserId() { return userId; }
    public double getAmountOwed() { return amountOwed; }
    public boolean isPaid() { return paid; }

    public void setPaid(boolean paid) { this.paid = paid; }
}