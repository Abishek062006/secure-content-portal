package com.secureportal.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A learner on a team. The database allows one team per learner per event. */
@Entity
@Table(name = "hackathon_team_members")
public class HackathonTeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false, updatable = false)
    private Long teamId;

    @Column(name = "hackathon_id", nullable = false, updatable = false)
    private Long hackathonId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected HackathonTeamMember() {
        // for JPA
    }

    public HackathonTeamMember(Long teamId, Long hackathonId, Long userId) {
        this.teamId = teamId;
        this.hackathonId = hackathonId;
        this.userId = userId;
        this.joinedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Long getHackathonId() {
        return hackathonId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

}
