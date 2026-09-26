package com.secureportal.course;

import com.secureportal.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(length = 80)
    private String category;

    @Column(name = "thumbnail_key", unique = true, length = 512)
    private String thumbnailKey;

    @Column(name = "thumbnail_mime", length = 128)
    private String thumbnailMime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private CourseStatus status = CourseStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "price_rupees", nullable = false)
    private int priceRupees;

    @Column(name = "discount_percent", nullable = false)
    private int discountPercent;

    @Column(name = "discount_start")
    private Instant discountStart;

    @Column(name = "discount_end")
    private Instant discountEnd;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

    protected Course() {
        // for JPA
    }

    public Course(String title, String description, String category, User uploadedBy) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.uploadedBy = uploadedBy;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getThumbnailKey() {
        return thumbnailKey;
    }

    public String getThumbnailMime() {
        return thumbnailMime;
    }

    public void setThumbnail(String key, String mime) {
        this.thumbnailKey = key;
        this.thumbnailMime = mime;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public void setStatus(CourseStatus status) {
        this.status = status;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getViewCount() {
        return viewCount;
    }

    public Instant getLastViewedAt() {
        return lastViewedAt;
    }


    public CoursePricing getPricing() {
        return new CoursePricing(priceRupees, discountPercent, discountStart, discountEnd);
    }

    public void setPricing(CoursePricing pricing) {
        this.priceRupees = pricing.priceRupees();
        this.discountPercent = pricing.discountPercent();
        this.discountStart = pricing.discountStart();
        this.discountEnd = pricing.discountEnd();
        this.updatedAt = Instant.now();
    }
}
