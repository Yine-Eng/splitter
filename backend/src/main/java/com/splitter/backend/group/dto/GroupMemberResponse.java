package com.splitter.backend.group.dto;

import java.time.LocalDateTime;

import com.splitter.backend.group.model.GroupRole;

public class GroupMemberResponse {

    private Long userId;
    private String username;
    private GroupRole role;
    private LocalDateTime joinedAt;

    public GroupMemberResponse(Long userId, String username, GroupRole role, LocalDateTime joinedAt) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public GroupRole getRole() {
        return role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
