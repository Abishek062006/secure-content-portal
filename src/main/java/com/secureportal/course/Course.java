package com.secureportal.course;

import com.secureportal.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "video_key", nullable = false, unique = true, length = 512)
    private String videoKey;

    @Column(name = "video_filename")
    private String videoFilename;

    @Column(name = "video_mime", nullable = false, length = 128)
    private String videoMime;

    @Column(name = "video_size", nullable = false)
    private long videoSize;

    @Column(name = "thumbnail_key", unique = true, length = 512)
    private String thumbnailKey;

    @Column(name = "thumbnail_mime", length = 128)
    private String thumbnailMime;

    @Column(name = "transcript_key", unique = true, length = 512)
    private String transcriptKey;

    @Column(name = "transcript_filename")
    private String transcriptFilename;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "last_viewed_at")
    private Instant lastViewedAt;

    protected Course() {
        // for JPA
    }

    public Course(String title, String description, String category, String videoKey, String videoFilename,
                  String videoMime, long videoSize, User uploadedBy) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.videoKey = videoKey;
        this.videoFilename = videoFilename;
        this.videoMime = videoMime;
        this.videoSize = videoSize;
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

    public String getVideoKey() {
        return videoKey;
    }

    public String getVideoFilename() {
        return videoFilename;
    }

    public String getVideoMime() {
        return videoMime;
    }

    public long getVideoSize() {
        return videoSize;
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

    public String getTranscriptKey() {
        return transcriptKey;
    }

    public String getTranscriptFilename() {
        return transcriptFilename;
    }

    public void setTranscript(String key, String filename) {
        this.transcriptKey = key;
        this.transcriptFilename = filename;
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

    public String getVideoSizeLabel() {
        if (videoSize < 1024 * 1024) {
            return String.format("%.0f KB", videoSize / 1024.0);
        }
        if (videoSize < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", videoSize / (1024.0 * 1024.0));
        }
        return String.format("%.2f GB", videoSize / (1024.0 * 1024.0 * 1024.0));
    }
}
