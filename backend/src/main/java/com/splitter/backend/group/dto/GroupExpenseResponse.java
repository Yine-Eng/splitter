package com.splitter.backend.group.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class GroupExpenseResponse {

    private UUID expenseId;
    private UUID groupId;
    private String description;
    private BigDecimal amount;
    private Long paidByUserId;
    private LocalDateTime createdAt;

    public GroupExpenseResponse(
            UUID expenseId,
            UUID groupId,
            String description,
            BigDecimal amount,
            Long paidByUserId,
            LocalDateTime createdAt) {
        this.expenseId = expenseId;
        this.groupId = groupId;
        this.description = description;
        this.amount = amount;
        this.paidByUserId = paidByUserId;
        this.createdAt = createdAt;
    }

    public UUID getExpenseId() {
        return expenseId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getPaidByUserId() {
        return paidByUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
