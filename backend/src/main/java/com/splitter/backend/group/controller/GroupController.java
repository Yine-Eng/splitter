package com.splitter.backend.group.controller;

import com.splitter.backend.group.model.Group;
import com.splitter.backend.group.service.GroupService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public Group createGroup(
            @RequestParam(required = false) String name,
            @RequestBody(required = false) CreateGroupRequest body,
            Authentication authentication
    ) {

        String groupName = (name != null ? name : (body != null ? body.name() : null));

        if (groupName == null || groupName.trim().isEmpty()) {
            throw new RuntimeException("Group name is required");
        }

        String username = authentication.getName();

        return groupService.createGroup(groupName.trim(), username);
    }

    public static record CreateGroupRequest(String name) {}
}