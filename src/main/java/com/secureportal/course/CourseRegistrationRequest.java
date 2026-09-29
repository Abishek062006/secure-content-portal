package com.secureportal.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_registration_requests")
public class CourseRegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RegistrationStatus status = RegistrationStatus.PENDING;

    @Column(length = 1000)
    private String message;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by", length = 320)
    private String decidedBy;

    protected CourseRegistrationRequest() {
        // for JPA
    }

    public CourseRegistrationRequest(UUID courseId, Long userId, String message) {
        this.courseId = courseId;
        this.userId = userId;
        this.message = message;
    }

    /** A denied (or just-resubmitted) request goes back to a clean, undecided state with the new message. */
    public void resubmit(String message) {
        this.status = RegistrationStatus.PENDING;
        this.message = message;
        this.decisionNote = null;
        this.decidedAt = null;
        this.decidedBy = null;
        this.requestedAt = Instant.now();
    }

    public void approve(String adminEmail, String note) {
        this.status = RegistrationStatus.APPROVED;
        this.decidedBy = adminEmail;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
    }

    public void deny(String adminEmail, String note) {
        this.status = RegistrationStatus.DENIED;
        this.decidedBy = adminEmail;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public Long getUserId() {
        return userId;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }
}
