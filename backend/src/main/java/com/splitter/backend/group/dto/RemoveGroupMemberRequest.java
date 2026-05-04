package com.splitter.backend.group.dto;

public record RemoveGroupMemberRequest(
        boolean confirmOutstandingBalances) {
}
