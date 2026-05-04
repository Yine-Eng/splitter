package com.splitter.backend.event.repository;

import com.splitter.backend.event.model.GroupEvent;
import com.splitter.backend.event.model.GroupEventVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GroupEventRepository extends JpaRepository<GroupEvent, UUID> {

    List<GroupEvent> findByGroupIdAndVisibilityOrderByCreatedAtDesc(
            UUID groupId,
            GroupEventVisibility visibility);

    @Query("SELECT e FROM GroupEvent e WHERE e.groupId = :groupId AND e.visibility = :visibility AND (e.actorUserId = :userId OR e.targetUserId = :userId) ORDER BY e.createdAt DESC")
    List<GroupEvent> findPrivateEventsForUser(
            @Param("groupId") UUID groupId,
            @Param("visibility") GroupEventVisibility visibility,
            @Param("userId") Long userId);
}
