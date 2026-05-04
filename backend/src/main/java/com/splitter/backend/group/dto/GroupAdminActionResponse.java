package com.splitter.backend.group.dto;

import com.splitter.backend.group.model.GroupRole;

import java.time.LocalDateTime;
import java.util.UUID;

public class GroupAdminActionResponse {

    private UUID groupId;
    private String groupName;
    private Long targetUserId;
    private GroupRole targetRole;
    private boolean archived;
    private LocalDateTime updatedAt;

    public GroupAdminActionResponse(
            UUID groupId,
            String groupName,
            Long targetUserId,
            GroupRole targetRole,
            boolean archived,
            LocalDateTime updatedAt) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.targetUserId = targetUserId;
        this.targetRole = targetRole;
        this.archived = archived;
        this.updatedAt = updatedAt;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public GroupRole getTargetRole() {
        return targetRole;
    }

    public boolean isArchived() {
        return archived;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
