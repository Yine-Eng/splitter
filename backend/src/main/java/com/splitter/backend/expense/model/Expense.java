package com.splitter.backend.expense.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID groupId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private Long paidByUserId;

    private LocalDateTime createdAt;

    public Expense() {}

    public Expense(UUID groupId, String description, double amount, Long paidByUserId) {
        this.groupId = groupId;
        this.description = description;
        this.amount = amount;
        this.paidByUserId = paidByUserId;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public String getDescription() { return description; }
    public double getAmount() { return amount; }
    public Long getPaidByUserId() { return paidByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setDescription(String description) { this.description = description; }
}