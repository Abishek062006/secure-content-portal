package com.secureportal.video;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "video_uploads")
public class VideoUpload {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private String filename;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "part_size", nullable = false)
    private long partSize;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "s3_upload_id", nullable = false, length = 512)
    private String s3UploadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UploadStatus status = UploadStatus.UPLOADING;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected VideoUpload() {
        // for JPA
    }

    public VideoUpload(UUID id, UUID moduleId, UUID courseId, String title, String description, String filename,
                       long sizeBytes, long partSize, String storageKey, String s3UploadId, Long createdBy) {
        this.id = id;
        this.moduleId = moduleId;
        this.courseId = courseId;
        this.title = title;
        this.description = description;
        this.filename = filename;
        this.sizeBytes = sizeBytes;
        this.partSize = partSize;
        this.storageKey = storageKey;
        this.s3UploadId = s3UploadId;
        this.createdBy = createdBy;
    }

    public int partCount() {
        return (int) ((sizeBytes + partSize - 1) / partSize);
    }

    public void markComplete() {
        this.status = UploadStatus.COMPLETE;
        this.completedAt = Instant.now();
    }

    public void markAborted() {
        this.status = UploadStatus.ABORTED;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getModuleId() {
        return moduleId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getFilename() {
        return filename;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public long getPartSize() {
        return partSize;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getS3UploadId() {
        return s3UploadId;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }
}
