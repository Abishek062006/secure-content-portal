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
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt = Instant.now();

    @Column(name = "last_lesson_id")
    private UUID lastLessonId;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    protected Enrollment() {
        // for JPA
    }

    public Enrollment(Long userId, UUID courseId) {
        this.userId = userId;
        this.courseId = courseId;
    }

    public void visit(UUID lessonId) {
        this.lastLessonId = lessonId;
        this.lastAccessedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public UUID getLastLessonId() {
        return lastLessonId;
    }
}
