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

/** A "tell me more, contact me" submission — on every course, whatever its access type. The admin follows
 *  up outside this system (a call, an email); {@link #markContacted} only records that they did. */
@Entity
@Table(name = "course_enquiries")
public class CourseEnquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 32)
    private String phone;

    @Column(length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EnquiryStatus status = EnquiryStatus.NEW;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "contacted_at")
    private Instant contactedAt;

    @Column(name = "contacted_by", length = 320)
    private String contactedBy;

    protected CourseEnquiry() {
        // for JPA
    }

    public CourseEnquiry(UUID courseId, Long userId, String name, String email, String phone, String message) {
        this.courseId = courseId;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.message = message;
    }

    public void markContacted(String adminEmail) {
        this.status = EnquiryStatus.CONTACTED;
        this.contactedBy = adminEmail;
        this.contactedAt = Instant.now();
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

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getMessage() {
        return message;
    }

    public EnquiryStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getContactedAt() {
        return contactedAt;
    }

    public String getContactedBy() {
        return contactedBy;
    }
}
