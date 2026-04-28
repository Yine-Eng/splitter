package com.splitter.backend.group.repository;

import com.splitter.backend.group.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {
    
    List<Group> findByIdIn(List<UUID> ids);
}
