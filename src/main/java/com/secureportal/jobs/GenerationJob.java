package com.secureportal.jobs;

import com.secureportal.quiz.Difficulty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "generation_jobs")
public class GenerationJob {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Difficulty difficulty;

    @Column(name = "final_only", nullable = false)
    private boolean finalOnly;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JobStatus status = JobStatus.QUEUED;

    @Column(nullable = false)
    private int produced;

    @Column(length = 500)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected GenerationJob() {
        // for JPA
    }

    public GenerationJob(UUID courseId, UUID lessonId, Long requestedBy, int requestedCount, Difficulty difficulty, boolean finalOnly) {
        this.courseId = courseId;
        this.lessonId = lessonId;
        this.requestedBy = requestedBy;
        this.requestedCount = requestedCount;
        this.difficulty = difficulty;
        this.finalOnly = finalOnly;
    }

    public void start() {
        this.status = JobStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void progress(int produced) {
        this.produced = produced;
    }

    public void finish(int produced, String message) {
        this.status = JobStatus.DONE;
        this.produced = produced;
        this.message = message;
        this.finishedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = JobStatus.FAILED;
        this.message = message == null ? null : message.substring(0, Math.min(500, message.length()));
        this.finishedAt = Instant.now();
    }

    public boolean isOpen() {
        return status == JobStatus.QUEUED || status == JobStatus.RUNNING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public int getRequestedCount() {
        return requestedCount;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public boolean isFinalOnly() {
        return finalOnly;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getProduced() {
        return produced;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
