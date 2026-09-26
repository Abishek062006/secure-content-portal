package com.secureportal.feed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "image_key")
    private String imageKey;

    @Column(name = "image_mime")
    private String imageMime;

    @Column(name = "video_key")
    private String videoKey;

    @Column(name = "video_mime")
    private String videoMime;

    @Column(nullable = false, length = 10)
    private String kind = "POST";

    private String title;

    @Column(name = "certificate_id")
    private UUID certificateId;

    @Column(name = "course_id")
    private UUID courseId;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "publish_at", nullable = false)
    private Instant publishAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Post() {
        // for JPA
    }

    public Post(Long authorId, String body, UUID courseId, boolean pinned, Instant publishAt) {
        this.authorId = authorId;
        this.body = body;
        this.courseId = courseId;
        this.pinned = pinned;
        if (publishAt != null) {
            this.publishAt = publishAt;
        }
    }

    public void edit(String body, UUID courseId, boolean pinned, Instant publishAt) {
        this.body = body;
        this.courseId = courseId;
        this.pinned = pinned;
        if (publishAt != null) {
            this.publishAt = publishAt;
        }
        this.updatedAt = Instant.now();
    }

    public void setImage(String key, String mime) {
        this.imageKey = key;
        this.imageMime = mime;
    }

    public void setVideo(String key, String mime) {
        this.videoKey = key;
        this.videoMime = mime;
    }

    /** Turns this into an article with a headline; articles are longer than posts. */
    public void setArticle(String title) {
        this.kind = "ARTICLE";
        this.title = title;
    }

    public void setCertificateId(UUID certificateId) {
        this.certificateId = certificateId;
    }

    public boolean isPublished() {
        return !publishAt.isAfter(Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    public String getImageKey() {
        return imageKey;
    }

    public String getImageMime() {
        return imageMime;
    }

    public String getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public UUID getCertificateId() {
        return certificateId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getVideoKey() {
        return videoKey;
    }

    public String getVideoMime() {
        return videoMime;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public boolean isPinned() {
        return pinned;
    }

    public Instant getPublishAt() {
        return publishAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
