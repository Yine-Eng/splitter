package com.splitter.backend.group.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "splitter_groups")
public class Group {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long createdByUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Group() {}

    public Group(String name, Long createdByUserId) {
        this.name = name;
        this.createdByUserId = createdByUserId;
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() { 
        return id; 
    }

    public String getName() { 
        return name; 
    }

    public Long getCreatedByUserId() { 
        return createdByUserId; 
    }

    public LocalDateTime getCreatedAt() { 
        return createdAt; 
    }

    public void setName(String name) { 
        this.name = name; 
    }
}