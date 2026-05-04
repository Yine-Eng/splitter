package com.splitter.backend.event.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "group_events")
public class GroupEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupEventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupEventVisibility visibility;

    @Column(nullable = false)
    private Long actorUserId;

    private Long targetUserId;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 1000)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public GroupEvent() {
    }

    public GroupEvent(
            UUID groupId,
            GroupEventType type,
            GroupEventVisibility visibility,
            Long actorUserId,
            Long targetUserId,
            BigDecimal amount,
            String message) {
        this.groupId = groupId;
        this.type = type;
        this.visibility = visibility;
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.amount = amount;
        this.message = message;
        this.createdAt = LocalDateTime.now();
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
