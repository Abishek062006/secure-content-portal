package com.secureportal.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A problem statement or challenge definition set by organizers for a hackathon. */
@Entity
@Table(name = "hackathon_problem_statements")
public class HackathonProblemStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hackathon_id", nullable = false, updatable = false)
    private Long hackathonId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "track", length = 100)
    private String track;

    @Column(name = "requirements", columnDefinition = "TEXT")
    private String requirements;

    @Column(name = "evaluation_criteria", columnDefinition = "TEXT")
    private String evaluationCriteria;

    @Column(name = "resources_url", length = 1000)
    private String resourcesUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected HackathonProblemStatement() {
        // for JPA
    }

    public HackathonProblemStatement(Long hackathonId, String title, String description, String track,
                                     String requirements, String evaluationCriteria, String resourcesUrl) {
        this.hackathonId = hackathonId;
        this.createdAt = Instant.now();
        update(title, description, track, requirements, evaluationCriteria, resourcesUrl);
    }

    public final void update(String title, String description, String track,
                             String requirements, String evaluationCriteria, String resourcesUrl) {
        this.title = title;
        this.description = description;
        this.track = track;
        this.requirements = requirements;
        this.evaluationCriteria = evaluationCriteria;
        this.resourcesUrl = resourcesUrl;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getHackathonId() {
        return hackathonId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getTrack() {
        return track;
    }

    public String getRequirements() {
        return requirements;
    }

    public String getEvaluationCriteria() {
        return evaluationCriteria;
    }

    public String getResourcesUrl() {
        return resourcesUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
