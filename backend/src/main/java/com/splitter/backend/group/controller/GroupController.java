package com.splitter.backend.group.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.splitter.backend.group.dto.AddGroupMemberRequest;
import com.splitter.backend.group.dto.GroupExpenseResponse;
import com.splitter.backend.group.dto.GroupMemberResponse;
import com.splitter.backend.group.dto.GroupSummaryResponse;
import com.splitter.backend.group.model.Group;
import com.splitter.backend.group.service.GroupService;
import com.splitter.backend.group.dto.GroupMemberRemovalPreviewResponse;
import com.splitter.backend.group.dto.RemoveGroupMemberRequest;

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
            Authentication authentication) {
        String groupName = (name != null ? name : (body != null ? body.name() : null));

        if (groupName == null || groupName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group name is required");
        }

        return groupService.createGroup(groupName.trim(), authentication.getName());
    }

    @GetMapping
    public List<GroupSummaryResponse> getMyGroups(Authentication authentication) {
        return groupService.getGroupsForUser(authentication.getName());
    }

    @PostMapping("/{groupId}/members")
    public GroupMemberResponse addMember(
            @PathVariable UUID groupId,
            @RequestBody AddGroupMemberRequest request,
            Authentication authentication) {
        return groupService.addMember(groupId, authentication.getName(), request.username());
    }

    @GetMapping("/{groupId}/members")
    public List<GroupMemberResponse> getGroupMembers(
            @PathVariable UUID groupId,
            Authentication authentication) {
        return groupService.getGroupMembers(groupId, authentication.getName());
    }

    @GetMapping("/{groupId}/expenses")
    public List<GroupExpenseResponse> getGroupExpenses(
            @PathVariable UUID groupId,
            Authentication authentication) {
        return groupService.getGroupExpenses(groupId, authentication.getName());
    }

    @GetMapping("/{groupId}/members/{userId}/removal-preview")
    public GroupMemberRemovalPreviewResponse previewMemberRemoval(
            @PathVariable UUID groupId,
            @PathVariable Long userId,
            Authentication authentication) {
        return groupService.previewMemberRemoval(groupId, userId, authentication.getName());
    }

    @PostMapping("/{groupId}/members/{userId}/remove")
    public GroupMemberRemovalPreviewResponse removeMember(
            @PathVariable UUID groupId,
            @PathVariable Long userId,
            @RequestBody(required = false) RemoveGroupMemberRequest request,
            Authentication authentication) {
        boolean confirmOutstandingBalances = request != null && request.confirmOutstandingBalances();

        return groupService.removeMember(
                groupId,
                userId,
                authentication.getName(),
                confirmOutstandingBalances);
    }

    public static record CreateGroupRequest(String name) {
    }
}
