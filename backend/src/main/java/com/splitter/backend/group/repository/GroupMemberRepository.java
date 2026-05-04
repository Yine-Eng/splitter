package com.splitter.backend.group.repository;

import com.splitter.backend.group.model.GroupMember;
import com.splitter.backend.group.model.GroupRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    List<GroupMember> findByGroupId(UUID groupId);

    List<GroupMember> findByGroupIdAndActiveTrue(UUID groupId);

    List<GroupMember> findByUserId(Long userId);

    List<GroupMember> findByUserIdAndActiveTrue(Long userId);

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, Long userId);

    Optional<GroupMember> findByGroupIdAndUserIdAndActiveTrue(UUID groupId, Long userId);

    long countByGroupIdAndRoleAndActiveTrue(UUID groupId, GroupRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM GroupMember m WHERE m.groupId = :groupId AND m.role = :role AND m.active = true")
    List<GroupMember> findByGroupIdAndRoleAndActiveTrueWithLock(@Param("groupId") UUID groupId, @Param("role") GroupRole role);
}
