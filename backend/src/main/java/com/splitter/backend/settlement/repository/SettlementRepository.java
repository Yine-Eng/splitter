package com.splitter.backend.settlement.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.splitter.backend.settlement.model.Settlement;
import com.splitter.backend.settlement.model.SettlementStatus;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    List<Settlement> findByToUserIdAndStatusOrderByCreatedAtDesc(Long toUserId, SettlementStatus status);

    List<Settlement> findByFromUserIdAndStatusOrderByCreatedAtDesc(Long fromUserId, SettlementStatus status);

    List<Settlement> findByGroupIdAndStatus(UUID groupId, SettlementStatus status);

    List<Settlement> findByGroupIdAndStatusOrderByCreatedAtDesc(UUID groupId, SettlementStatus status);

    List<Settlement> findByGroupIdAndFromUserIdAndToUserIdAndStatus(
            UUID groupId,
            Long fromUserId,
            Long toUserId,
            SettlementStatus status
    );
}
