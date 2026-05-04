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

    @Column(nullable = false)
    private Integer remainderStartIndex;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean archived = false;

    private LocalDateTime archivedAt;

    public Group() {
    }

    public Group(String name, Long createdByUserId) {
        this.name = name;
        this.createdByUserId = createdByUserId;
        this.createdAt = LocalDateTime.now();
        this.remainderStartIndex = 0;
        this.archived = false;
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

    public Integer getRemainderStartIndex() {
        return remainderStartIndex;
    }

    public boolean isArchived() {
        return archived;
    }

    public LocalDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRemainderStartIndex(Integer remainderStartIndex) {
        this.remainderStartIndex = remainderStartIndex;
    }

    public void archive() {
        this.archived = true;
        this.archivedAt = LocalDateTime.now();
    }
}
