package com.secureportal.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A team's project. One per team; the team can save drafts and change it until the submission deadline. */
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

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @Column(name = "demo_url", length = 500)
    private String demoUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubmissionStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HackathonSubmission() {
        // for JPA
    }

    public HackathonSubmission(Long teamId, Long hackathonId, String title, String repoUrl, String demoUrl, String description,
                               SubmissionStatus status) {
        this.teamId = teamId;
        this.hackathonId = hackathonId;
        this.submittedAt = Instant.now();
        update(title, repoUrl, demoUrl, description, status);
    }

    /** Saves the team's latest version. `submittedAt` moves to the moment a draft is turned in. */
    public final void update(String title, String repoUrl, String demoUrl, String description, SubmissionStatus status) {
        if (status == SubmissionStatus.SUBMITTED && this.status != SubmissionStatus.SUBMITTED) {
            this.submittedAt = Instant.now();
        }
        this.title = title;
        this.repoUrl = repoUrl;
        this.demoUrl = demoUrl;
        this.description = description;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public boolean isSubmitted() {
        return status == SubmissionStatus.SUBMITTED;
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
