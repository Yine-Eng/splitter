package com.splitter.backend.event.dto;

import com.splitter.backend.event.model.GroupEventType;
import com.splitter.backend.event.model.GroupEventVisibility;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class GroupEventResponse {

    private UUID id;
    private UUID groupId;
    private GroupEventType type;
    private GroupEventVisibility visibility;
    private Long actorUserId;
    private Long targetUserId;
    private BigDecimal amount;
    private String message;
    private LocalDateTime createdAt;

    public GroupEventResponse(
            UUID id,
            UUID groupId,
            GroupEventType type,
            GroupEventVisibility visibility,
            Long actorUserId,
            Long targetUserId,
            BigDecimal amount,
            String message,
            LocalDateTime createdAt) {
        this.id = id;
        this.groupId = groupId;
        this.type = type;
        this.visibility = visibility;
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.amount = amount;
        this.message = message;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public GroupEventType getType() {
        return type;
    }

    public GroupEventVisibility getVisibility() {
        return visibility;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
