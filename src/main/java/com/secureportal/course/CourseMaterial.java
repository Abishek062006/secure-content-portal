package com.secureportal.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "module_materials")
public class CourseMaterial {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MaterialKind kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "storage_key")
    private String storageKey;

    private String filename;

    private String mime;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(length = 1000)
    private String url;

    @Column(nullable = false)
    private boolean downloadable;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected CourseMaterial() {
        // for JPA
    }

    public CourseMaterial(UUID id, UUID moduleId, UUID courseId, MaterialKind kind, String title, String description,
                          boolean downloadable) {
        this.id = id;
        this.moduleId = moduleId;
        this.courseId = courseId;
        this.kind = kind;
        this.title = title;
        this.description = description;
        this.downloadable = downloadable;
    }

    public void setFile(String storageKey, String filename, String mime, long sizeBytes, Integer pageCount) {
        this.storageKey = storageKey;
        this.filename = filename;
        this.mime = mime;
        this.sizeBytes = sizeBytes;
        this.pageCount = pageCount;
    }

    public void setLink(String url) {
        this.url = url;
    }

    public void edit(String title, String description, boolean downloadable) {
        this.title = title;
        this.description = description;
        this.downloadable = downloadable;
    }

    public String getSizeLabel() {
        if (sizeBytes == null) {
            return null;
        }
        if (sizeBytes < 1024 * 1024) {
            return String.format("%.0f KB", Math.max(1, sizeBytes / 1024.0));
        }
        return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
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

    public MaterialKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getFilename() {
        return filename;
    }

    public String getMime() {
        return mime;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public String getUrl() {
        return url;
    }

    public boolean isDownloadable() {
        return downloadable;
    }
}
