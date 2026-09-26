package com.secureportal.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Someone an admin has asked to score a hosted event's submissions. */
@Entity
@Table(name = "hackathon_judges")
public class HackathonJudge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hackathon_id", nullable = false, updatable = false)
    private Long hackathonId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected HackathonJudge() {
        // for JPA
    }

    public HackathonJudge(Long hackathonId, Long userId) {
        this.hackathonId = hackathonId;
        this.userId = userId;
        this.assignedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getHackathonId() {
        return hackathonId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

}
