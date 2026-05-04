package com.splitter.backend.event.controller;

import com.splitter.backend.event.dto.GroupEventResponse;
import com.splitter.backend.event.service.GroupEventService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
public class GroupEventController {

    private final GroupEventService groupEventService;

    public GroupEventController(GroupEventService groupEventService) {
        this.groupEventService = groupEventService;
    }

    @GetMapping("/{groupId}/events")
    public List<GroupEventResponse> getGroupEvents(
            @PathVariable UUID groupId,
            Authentication authentication) {
        return groupEventService.getGroupEvents(groupId, authentication.getName());
    }
}
