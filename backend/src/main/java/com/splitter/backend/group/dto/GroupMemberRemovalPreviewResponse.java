package com.splitter.backend.group.dto;

import java.util.List;
import java.util.UUID;

public class GroupMemberRemovalPreviewResponse {

    private UUID groupId;
    private Long userId;
    private String username;
    private List<RemovalBalanceItem> userOwes;
    private List<RemovalBalanceItem> userIsOwed;
    private int pendingSettlementCount;
    private boolean canRemoveWithoutConfirmation;

    public GroupMemberRemovalPreviewResponse(
            UUID groupId,
            Long userId,
            String username,
            List<RemovalBalanceItem> userOwes,
            List<RemovalBalanceItem> userIsOwed,
            int pendingSettlementCount,
            boolean canRemoveWithoutConfirmation) {
        this.groupId = groupId;
        this.userId = userId;
        this.username = username;
        this.userOwes = userOwes;
        this.userIsOwed = userIsOwed;
        this.pendingSettlementCount = pendingSettlementCount;
        this.canRemoveWithoutConfirmation = canRemoveWithoutConfirmation;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public List<RemovalBalanceItem> getUserOwes() {
        return userOwes;
    }

    public List<RemovalBalanceItem> getUserIsOwed() {
        return userIsOwed;
    }

    public int getPendingSettlementCount() {
        return pendingSettlementCount;
    }

    public boolean isCanRemoveWithoutConfirmation() {
        return canRemoveWithoutConfirmation;
    }
}
