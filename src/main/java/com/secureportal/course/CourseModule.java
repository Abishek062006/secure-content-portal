package com.secureportal.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A named section of a course that groups lessons. (Not called Module: that clashes with java.lang.Module.) */
@Entity
@Table(name = "modules")
public class CourseModule {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CourseModule() {
        // for JPA
    }

    public CourseModule(UUID courseId, String title, String description, int position) {
        this.courseId = courseId;
        this.title = title;
        this.description = description;
        this.position = position;
    }

    public void edit(String title, String description) {
        this.title = title;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
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
}
