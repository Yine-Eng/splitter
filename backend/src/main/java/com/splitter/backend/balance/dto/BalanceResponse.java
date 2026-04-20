package com.splitter.backend.balance.dto;

import java.util.List;
import java.util.UUID;

public class BalanceResponse {

    private UUID groupId;
    private List<UserBalanceDto> balances;

    public BalanceResponse() {
    }

    public BalanceResponse(UUID groupId, List<UserBalanceDto> balances) {
        this.groupId = groupId;
        this.balances = balances;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public List<UserBalanceDto> getBalances() {
        return balances;
    }

    public void setBalances(List<UserBalanceDto> balances) {
        this.balances = balances;
    }
}
