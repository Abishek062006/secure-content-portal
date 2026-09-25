package com.secureportal.certificate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "certificates")
public class Certificate {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "recipient_name", nullable = false)
    private String recipientName;

    @Column(name = "course_title", nullable = false)
    private String courseTitle;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    protected Certificate() {
        // for JPA
    }

    public Certificate(String code, Long userId, UUID courseId, String recipientName, String courseTitle) {
        this.code = code;
        this.userId = userId;
        this.courseId = courseId;
        this.recipientName = recipientName;
        this.courseTitle = courseTitle;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Long getUserId() {
        return userId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
