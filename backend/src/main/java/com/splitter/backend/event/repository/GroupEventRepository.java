package com.splitter.backend.event.repository;

import com.splitter.backend.event.model.GroupEvent;
import com.splitter.backend.event.model.GroupEventVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GroupEventRepository extends JpaRepository<GroupEvent, UUID> {

    List<GroupEvent> findByGroupIdAndVisibilityOrderByCreatedAtDesc(
            UUID groupId,
            GroupEventVisibility visibility);

    List<GroupEvent> findByGroupIdAndVisibilityAndActorUserIdOrGroupIdAndVisibilityAndTargetUserIdOrderByCreatedAtDesc(
            UUID groupId1,
            GroupEventVisibility visibility1,
            Long actorUserId,
            UUID groupId2,
            GroupEventVisibility visibility2,
            Long targetUserId);
}
