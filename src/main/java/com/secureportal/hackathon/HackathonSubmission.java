package com.secureportal.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A team's project. One per team; the team can change it until the submission deadline. */
@Entity
@Table(name = "hackathon_submissions")
public class HackathonSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false, updatable = false)
    private Long teamId;

    @Column(name = "hackathon_id", nullable = false, updatable = false)
    private Long hackathonId;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "repo_url", nullable = false, length = 500)
    private String repoUrl;

    @Column(name = "demo_url", length = 500)
    private String demoUrl;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "SUBMITTED";

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HackathonSubmission() {
        // for JPA
    }

    public HackathonSubmission(Long teamId, Long hackathonId, String title, String repoUrl, String demoUrl, String description) {
        this(teamId, hackathonId, title, repoUrl, demoUrl, description, "SUBMITTED");
    }

    public HackathonSubmission(Long teamId, Long hackathonId, String title, String repoUrl, String demoUrl, String description, String status) {
        this.teamId = teamId;
        this.hackathonId = hackathonId;
        this.submittedAt = Instant.now();
        this.status = status != null ? status : "SUBMITTED";
        update(title, repoUrl, demoUrl, description, this.status);
    }

    public final void update(String title, String repoUrl, String demoUrl, String description) {
        update(title, repoUrl, demoUrl, description, this.status);
    }

    public final void update(String title, String repoUrl, String demoUrl, String description, String status) {
        this.title = title;
        this.repoUrl = repoUrl;
        this.demoUrl = demoUrl;
        this.description = description;
        if (status != null && !status.isBlank()) {
            this.status = status;
        }
        this.updatedAt = Instant.now();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getTitle() {
        return title;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getDemoUrl() {
        return demoUrl;
    }

    public String getDescription() {
        return description;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}
