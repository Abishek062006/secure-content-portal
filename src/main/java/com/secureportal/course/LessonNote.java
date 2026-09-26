package com.secureportal.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A learner's private note on a lesson, pinned to a moment in its video. Only its owner ever reads it. */
@Entity
@Table(name = "lesson_notes")
public class LessonNote {

    public static final int MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "video_seconds", nullable = false)
    private int videoSeconds;

    @Column(nullable = false, length = MAX_LENGTH)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected LessonNote() {
        // for JPA
    }

    public LessonNote(Long userId, UUID lessonId, UUID courseId, int videoSeconds, String body) {
        this.userId = userId;
        this.lessonId = lessonId;
        this.courseId = courseId;
        this.videoSeconds = Math.max(0, videoSeconds);
        this.body = body;
    }

    public void edit(String body) {
        this.body = body;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public int getVideoSeconds() {
        return videoSeconds;
    }

    public String getBody() {
        return body;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
