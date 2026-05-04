package com.splitter.backend.settlement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateSettlementRequest(
                UUID groupId,
                Long toUserId,
                BigDecimal amount,
                String note) {
}
