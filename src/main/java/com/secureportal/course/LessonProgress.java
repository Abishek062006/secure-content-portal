package com.secureportal.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lesson_progress")
public class LessonProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "position_seconds", nullable = false)
    private int positionSeconds;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected LessonProgress() {
        // for JPA
    }

    public LessonProgress(Long userId, UUID lessonId, UUID courseId) {
        this.userId = userId;
        this.lessonId = lessonId;
        this.courseId = courseId;
    }

    /** Completion is sticky: watching part of a finished lesson again never un-completes it. */
    public void record(int positionSeconds, boolean completed) {
        this.positionSeconds = Math.max(0, positionSeconds);
        if (completed && !this.completed) {
            this.completed = true;
            this.completedAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public int getPositionSeconds() {
        return positionSeconds;
    }

    public boolean isCompleted() {
        return completed;
    }
}
