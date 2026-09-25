package com.secureportal.quiz;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "question_text", nullable = false, length = 1000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Difficulty difficulty;

    @Column(length = 1000)
    private String explanation;

    @Column(name = "source_seconds")
    private Integer sourceSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private QuestionStatus status = QuestionStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private QuestionSource source;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "sort_order")
    private List<QuestionOption> options = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Question() {
        // for JPA
    }

    public Question(UUID courseId, UUID lessonId, String text, Difficulty difficulty, String explanation,
                    Integer sourceSeconds, QuestionSource source, QuestionStatus status, List<QuestionOption> options) {
        this.courseId = courseId;
        this.lessonId = lessonId;
        this.text = text;
        this.difficulty = difficulty;
        this.explanation = explanation;
        this.sourceSeconds = sourceSeconds;
        this.source = source;
        this.status = status;
        this.options = new ArrayList<>(options);
    }

    public void edit(String text, Difficulty difficulty, String explanation, List<QuestionOption> options) {
        this.text = text;
        this.difficulty = difficulty;
        this.explanation = explanation;
        this.options.clear();
        this.options.addAll(options);
        this.updatedAt = Instant.now();
    }

    public void setStatus(QuestionStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public QuestionSource getSource() {
        return source;
    }

    public String getText() {
        return text;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getExplanation() {
        return explanation;
    }

    public Integer getSourceSeconds() {
        return sourceSeconds;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public List<QuestionOption> getOptions() {
        return options;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
