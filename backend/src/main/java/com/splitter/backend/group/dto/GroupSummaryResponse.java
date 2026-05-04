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
    private boolean archived;

    public GroupSummaryResponse(UUID groupId, String name, Long createdByUserId, LocalDateTime createdAt,
            GroupRole role, boolean archived) {
        this.groupId = groupId;
        this.name = name;
        this.createdByUserId = createdByUserId;
        this.createdAt = createdAt;
        this.role = role;
        this.archived = archived;
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

    public boolean isArchived() {
        return archived;
    }
}
