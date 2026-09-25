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
@Table(name = "attempts")
public class Attempt {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "score_percent")
    private Integer scorePercent;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    private Boolean passed;

    @Column(name = "timed_out", nullable = false)
    private boolean timedOut;

    protected Attempt() {
        // for JPA
    }

    public Attempt(UUID assessmentId, Long userId, int totalCount, Instant expiresAt) {
        this.assessmentId = assessmentId;
        this.userId = userId;
        this.totalCount = totalCount;
        this.expiresAt = expiresAt;
    }

    public void grade(int correctCount, int totalCount, Integer scorePercent, Boolean passed, boolean timedOut) {
        this.status = AttemptStatus.SUBMITTED;
        this.submittedAt = Instant.now();
        this.correctCount = correctCount;
        this.totalCount = totalCount;
        this.scorePercent = scorePercent;
        this.passed = passed;
        this.timedOut = timedOut;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public Long getUserId() {
        return userId;
    }

    public AttemptStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Integer getScorePercent() {
        return scorePercent;
    }

    public Integer getCorrectCount() {
        return correctCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public Boolean getPassed() {
        return passed;
    }

    public boolean isTimedOut() {
        return timedOut;
    }
}
