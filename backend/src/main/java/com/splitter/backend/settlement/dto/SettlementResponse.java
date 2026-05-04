package com.splitter.backend.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.splitter.backend.settlement.model.SettlementStatus;

public class SettlementResponse {

    private UUID id;
    private UUID groupId;
    private Long fromUserId;
    private Long toUserId;
    private BigDecimal amount;
    private SettlementStatus status;
    private String note;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime rejectedAt;

    public SettlementResponse() {
    }

    public SettlementResponse(
            UUID id,
            UUID groupId,
            Long fromUserId,
            Long toUserId,
            BigDecimal amount,
            SettlementStatus status,
            String note,
            String rejectionReason,
            LocalDateTime createdAt,
            LocalDateTime confirmedAt,
            LocalDateTime rejectedAt) {
        this.id = id;
        this.groupId = groupId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
        this.status = status;
        this.note = note;
        this.rejectionReason = rejectionReason;
        this.createdAt = createdAt;
        this.confirmedAt = confirmedAt;
        this.rejectedAt = rejectedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }
}
