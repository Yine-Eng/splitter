package com.splitter.backend.group.repository;

import com.splitter.backend.group.model.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    List<GroupMember> findByGroupId(UUID groupId);

    List<GroupMember> findByUserId(Long userId);

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, Long userId);
}