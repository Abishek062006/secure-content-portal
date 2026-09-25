package com.secureportal.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One video (plus an optional transcript) inside a module. */
@Entity
@Table(name = "lessons")
public class Lesson {

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
    private int position;

    @Column(name = "video_key", nullable = false, unique = true, length = 512)
    private String videoKey;

    @Column(name = "video_filename")
    private String videoFilename;

    @Column(name = "video_mime", nullable = false, length = 128)
    private String videoMime;

    @Column(name = "video_size", nullable = false)
    private long videoSize;

    @Column(name = "transcript_key", unique = true, length = 512)
    private String transcriptKey;

    @Column(name = "transcript_filename")
    private String transcriptFilename;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Lesson() {
        // for JPA
    }

    public Lesson(UUID moduleId, UUID courseId, String title, String description, int position,
                  String videoKey, String videoFilename, String videoMime, long videoSize) {
        this.moduleId = moduleId;
        this.courseId = courseId;
        this.title = title;
        this.description = description;
        this.position = position;
        this.videoKey = videoKey;
        this.videoFilename = videoFilename;
        this.videoMime = videoMime;
        this.videoSize = videoSize;
    }

    public void edit(String title, String description) {
        this.title = title;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void setTranscript(String key, String filename) {
        this.transcriptKey = key;
        this.transcriptFilename = filename;
        this.updatedAt = Instant.now();
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

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
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

    public String getTranscriptKey() {
        return transcriptKey;
    }

    public String getTranscriptFilename() {
        return transcriptFilename;
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
