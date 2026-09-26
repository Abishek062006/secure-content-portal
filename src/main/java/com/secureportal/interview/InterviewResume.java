package com.secureportal.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** The text of a learner's resume, kept only to build their interview questions. The uploaded file itself is never stored. */
@Entity
@Table(name = "interview_resumes")
public class InterviewResume {

    private static final int PREVIEW = 400;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "original_filename", nullable = false, length = 200)
    private String originalFilename;

    @Column(name = "content_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String contentText;

    @Column(nullable = false)
    private int characters;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected InterviewResume() {
        // for JPA
    }

    public InterviewResume(Long userId, String originalFilename, String contentText, Instant uploadedAt, Instant expiresAt) {
        this.userId = userId;
        this.originalFilename = originalFilename;
        this.contentText = contentText;
        this.characters = contentText.length();
        this.uploadedAt = uploadedAt;
        this.expiresAt = expiresAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentText() {
        return contentText;
    }

    public int getCharacters() {
        return characters;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** The first few lines, so the learner can see the text was read properly without the whole resume travelling back. */
    public String preview() {
        return contentText.length() <= PREVIEW ? contentText : contentText.substring(0, PREVIEW) + "...";
    }
}
