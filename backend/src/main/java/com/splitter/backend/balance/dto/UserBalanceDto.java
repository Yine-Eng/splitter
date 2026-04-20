package com.splitter.backend.balance.dto;

public class UserBalanceDto {

    private Long fromUserId;
    private Long toUserId;
    private double amount;

    public UserBalanceDto() {
    }

    public UserBalanceDto(Long fromUserId, Long toUserId, double amount) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(Long fromUserId) {
        this.fromUserId = fromUserId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }
}
