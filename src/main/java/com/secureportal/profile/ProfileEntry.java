package com.secureportal.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profile_entries")
public class ProfileEntry {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EntryKind kind;

    @Column(nullable = false, length = 200)
    private String title;

    private String subtitle;

    @Column(name = "start_year")
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;

    @Column(length = 1500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ProfileEntry() {
        // for JPA
    }

    public ProfileEntry(Long userId, EntryKind kind) {
        this.userId = userId;
        this.kind = kind;
    }

    public void set(String title, String subtitle, Integer startYear, Integer endYear, String description) {
        this.title = title;
        this.subtitle = subtitle;
        this.startYear = startYear;
        this.endYear = endYear;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public EntryKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public Integer getStartYear() {
        return startYear;
    }

    public Integer getEndYear() {
        return endYear;
    }

    public String getDescription() {
        return description;
    }
}
