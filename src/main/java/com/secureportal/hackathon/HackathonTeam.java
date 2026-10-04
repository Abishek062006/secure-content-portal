package com.secureportal.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A team in a hosted hackathon. A learner who enters alone is a team of one. */
@Entity
@Table(name = "hackathon_teams")
public class HackathonTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hackathon_id", nullable = false, updatable = false)
    private Long hackathonId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "track", length = 100)
    private String track;

    @Column(name = "invite_code", nullable = false, updatable = false, length = 16)
    private String inviteCode;

    @Column(name = "leader_id", nullable = false)
    private Long leaderId;

    @Column(name = "problem_statement_id")
    private Long problemStatementId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HackathonTeam() {
        // for JPA
    }

    public HackathonTeam(Long hackathonId, String name, String track, String inviteCode, Long leaderId) {
        this.hackathonId = hackathonId;
        this.name = name;
        this.track = track;
        this.inviteCode = inviteCode;
        this.leaderId = leaderId;
        this.createdAt = Instant.now();
    }

    public void handLeadershipTo(Long userId) {
        this.leaderId = userId;
    }

    public Long getId() {
        return id;
    }

    public Long getHackathonId() {
        return hackathonId;
    }

    public String getName() {
        return name;
    }

    public String getTrack() {
        return track;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public Long getLeaderId() {
        return leaderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getProblemStatementId() {
        return problemStatementId;
    }

    public void selectProblemStatement(Long problemStatementId) {
        this.problemStatementId = problemStatementId;
    }

}
