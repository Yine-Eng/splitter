package com.splitter.backend.expense.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
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

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amountOwed;

    @Column(nullable = false)
    private boolean paid;

    public ExpenseSplit() {
    }

    public ExpenseSplit(UUID expenseId, Long userId, BigDecimal amountOwed) {
        this.expenseId = expenseId;
        this.userId = userId;
        this.amountOwed = amountOwed;
        this.paid = false;
    }

    public UUID getExpenseId() {
        return expenseId;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAmountOwed() {
        return amountOwed;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }
}