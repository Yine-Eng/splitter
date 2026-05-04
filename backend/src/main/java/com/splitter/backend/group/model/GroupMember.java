package com.splitter.backend.group.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "splitter_group_members")
public class GroupMember {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID groupId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupRole role;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    private LocalDateTime removedAt;

    public GroupMember() {
    }

    public GroupMember(UUID groupId, Long userId, GroupRole role) {
        this.groupId = groupId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = LocalDateTime.now();
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public GroupRole getRole() {
        return role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getRemovedAt() {
        return removedAt;
    }

    public void setRole(GroupRole role) {
        this.role = role;
    }

    public void markRemoved() {
        this.active = false;
        this.removedAt = LocalDateTime.now();
    }
}
