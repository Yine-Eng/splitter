package com.splitter.backend.group.repository;

import com.splitter.backend.group.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {
}