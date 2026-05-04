package com.splitter.backend.group.dto;

import java.math.BigDecimal;

public class RemovalBalanceItem {

    private Long fromUserId;
    private Long toUserId;
    private BigDecimal amount;

    public RemovalBalanceItem(Long fromUserId, Long toUserId, BigDecimal amount) {
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
    }

    public Long getFromUserId() {
        return fromUserId;
    }

    public Long getToUserId() {
        return toUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
