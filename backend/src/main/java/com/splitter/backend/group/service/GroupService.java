package com.splitter.backend.group.service;

import com.splitter.backend.group.model.Group;
import com.splitter.backend.group.model.GroupMember;
import com.splitter.backend.group.model.GroupRole;
import com.splitter.backend.group.repository.GroupMemberRepository;
import com.splitter.backend.group.repository.GroupRepository;
import com.splitter.backend.models.User;
import com.splitter.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
    }

    public Group createGroup(String name, String creatorUsername) {
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Group group = new Group(name, creator.getId());
        Group savedGroup = groupRepository.save(group);

        GroupMember creatorMembership =
                new GroupMember(savedGroup.getId(), creator.getId(), GroupRole.ADMIN);

        groupMemberRepository.save(creatorMembership);

        return savedGroup;
    }

    public List<GroupMember> getGroupMembers(UUID groupId) {
        return groupMemberRepository.findByGroupId(groupId);
    }

    public void addMember(UUID groupId, Long adminId, Long newUserId) {

        GroupMember adminMembership =
                groupMemberRepository.findByGroupIdAndUserId(groupId, adminId)
                        .orElseThrow(() -> new RuntimeException("Not a group member"));

        if (adminMembership.getRole() != GroupRole.ADMIN) {
            throw new RuntimeException("Only admins can add members");
        }

        boolean userExists = userRepository.existsById(newUserId);
        if (!userExists) {
            throw new RuntimeException("Target user does not exist");
        }

        boolean alreadyMember = groupMemberRepository.findByGroupIdAndUserId(groupId, newUserId).isPresent();
        if (alreadyMember) {
            throw new RuntimeException("User is already in the group");
        }

        GroupMember newMember =
                new GroupMember(groupId, newUserId, GroupRole.MEMBER);

        groupMemberRepository.save(newMember);
    }
}