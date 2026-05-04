package com.splitter.backend.event.service;

import com.splitter.backend.event.dto.GroupEventResponse;
import com.splitter.backend.event.model.GroupEvent;
import com.splitter.backend.event.model.GroupEventType;
import com.splitter.backend.event.model.GroupEventVisibility;
import com.splitter.backend.event.repository.GroupEventRepository;
import com.splitter.backend.group.repository.GroupMemberRepository;
import com.splitter.backend.models.User;
import com.splitter.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class GroupEventService {

    private final GroupEventRepository groupEventRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public GroupEventService(
            GroupEventRepository groupEventRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository) {
        this.groupEventRepository = groupEventRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
    }

    public void createGroupEvent(
            UUID groupId,
            GroupEventType type,
            GroupEventVisibility visibility,
            Long actorUserId,
            Long targetUserId,
            BigDecimal amount,
            String message) {
        GroupEvent event = new GroupEvent(
                groupId,
                type,
                visibility,
                actorUserId,
                targetUserId,
                amount,
                message);

        groupEventRepository.save(event);
    }

    public List<GroupEventResponse> getGroupEvents(UUID groupId, String requesterUsername) {
        User requester = userRepository.findByUsername(requesterUsername)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user not found"));

        boolean isMember = groupMemberRepository
                .findByGroupIdAndUserId(groupId, requester.getId())
                .isPresent();

        if (!isMember) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }

        List<GroupEvent> groupEvents = groupEventRepository.findByGroupIdAndVisibilityOrderByCreatedAtDesc(
                groupId,
                GroupEventVisibility.GROUP);

        List<GroupEvent> privateEvents = groupEventRepository
                .findPrivateEventsForUser(
                        groupId,
                        GroupEventVisibility.PRIVATE,
                        requester.getId());

        List<GroupEvent> combinedEvents = new ArrayList<>();
        combinedEvents.addAll(groupEvents);
        combinedEvents.addAll(privateEvents);

        combinedEvents.sort(Comparator.comparing(GroupEvent::getCreatedAt).reversed());

        return combinedEvents.stream()
                .map(this::toResponse)
                .toList();
    }

    private GroupEventResponse toResponse(GroupEvent event) {
        return new GroupEventResponse(
                event.getId(),
                event.getGroupId(),
                event.getType(),
                event.getVisibility(),
                event.getActorUserId(),
                event.getTargetUserId(),
                event.getAmount(),
                event.getMessage(),
                event.getCreatedAt());
    }
}
