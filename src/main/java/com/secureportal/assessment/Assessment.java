package com.secureportal.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assessments")
public class Assessment {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** Null for the course's final assessment. */
    @Column(name = "module_id")
    private UUID moduleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AssessmentType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "easy_count", nullable = false)
    private int easyCount;

    @Column(name = "medium_count", nullable = false)
    private int mediumCount;

    @Column(name = "hard_count", nullable = false)
    private int hardCount;

    @Column(name = "pass_percent")
    private Integer passPercent;

    @Column(name = "time_limit_minutes")
    private Integer timeLimitMinutes;

    @Column(name = "max_attempts")
    private Integer maxAttempts;

    @Column(name = "gates_next", nullable = false)
    private boolean gatesNext;

    /** Final assessment only: the share (0-100) of questions recycled from the course's regular questions. */
    @Column(name = "reuse_percent")
    private Integer reusePercent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Assessment() {
        // for JPA
    }

    public Assessment(UUID courseId, UUID moduleId, AssessmentInput input) {
        this.courseId = courseId;
        this.moduleId = moduleId;
        apply(input);
    }

    public final void apply(AssessmentInput input) {
        this.type = input.type();
        this.title = input.title();
        this.easyCount = input.easyCount();
        this.mediumCount = input.mediumCount();
        this.hardCount = input.hardCount();
        this.passPercent = input.passPercent();
        this.timeLimitMinutes = input.timeLimitMinutes();
        this.maxAttempts = input.maxAttempts();
        this.gatesNext = input.gatesNext();
        this.reusePercent = input.reusePercent();
        this.updatedAt = Instant.now();
    }

    public boolean isGraded() {
        return type == AssessmentType.ASSESSMENT;
    }

    public int totalQuestions() {
        return easyCount + mediumCount + hardCount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getModuleId() {
        return moduleId;
    }

    public AssessmentType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public int getEasyCount() {
        return easyCount;
    }

    public int getMediumCount() {
        return mediumCount;
    }

    public int getHardCount() {
        return hardCount;
    }

    public Integer getPassPercent() {
        return passPercent;
    }

    public Integer getTimeLimitMinutes() {
        return timeLimitMinutes;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public boolean isGatesNext() {
        return gatesNext;
    }

    public Integer getReusePercent() {
        return reusePercent;
    }
}
