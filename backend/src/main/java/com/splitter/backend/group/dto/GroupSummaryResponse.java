package com.splitter.backend.group.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.splitter.backend.group.model.GroupRole;

public class GroupSummaryResponse {

    private UUID groupId;
    private String name;
    private Long createdByUserId;
    private LocalDateTime createdAt;
    private GroupRole role;

    public GroupSummaryResponse(UUID groupId, String name, Long createdByUserId, LocalDateTime createdAt,
            GroupRole role) {
        this.groupId = groupId;
        this.name = name;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.role = role;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public GroupRole getRole() {
        return role;
    }
}
