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

    /** Null for manually typed or imported questions — only the AI generator classifies this. */
    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private TranscriptGrounding grounding;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 16)
    private QuestionType type = QuestionType.MULTIPLE_CHOICE;

    /** The code a FILL_CODE or PREDICT_OUTPUT question is about; null for multiple choice. */
    @Column(name = "code_snippet", length = 4000)
    private String codeSnippet;

    /** For the coding types, whether learners pick from the options (true) or type the answer (false). */
    @Column(name = "show_options", nullable = false)
    private boolean showOptions = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "sort_order")
    private List<QuestionOption> options = new ArrayList<>();

    /** Other typed answers that count as right, besides the correct option's own text. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_accepted_answers", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "answer_text", nullable = false, length = 500)
    private List<String> acceptedAnswers = new ArrayList<>();

    /** Reserved for the final assessment: module quizzes never draw it. */
    @Column(name = "final_only", nullable = false)
    private boolean finalOnly;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Question() {
        // for JPA
    }

    public Question(UUID courseId, UUID lessonId, String text, Difficulty difficulty, String explanation,
                    Integer sourceSeconds, QuestionSource source, QuestionStatus status, List<QuestionOption> options,
                    TranscriptGrounding grounding) {
        this(courseId, lessonId, text, difficulty, explanation, sourceSeconds, source, status, options, grounding,
                QuestionFactory.Style.choice());
    }

    public Question(UUID courseId, UUID lessonId, String text, Difficulty difficulty, String explanation,
                    Integer sourceSeconds, QuestionSource source, QuestionStatus status, List<QuestionOption> options,
                    TranscriptGrounding grounding, QuestionFactory.Style style) {
        this.courseId = courseId;
        this.lessonId = lessonId;
        this.text = text;
        this.difficulty = difficulty;
        this.explanation = explanation;
        this.sourceSeconds = sourceSeconds;
        this.source = source;
        this.status = status;
        this.options = new ArrayList<>(options);
        this.grounding = grounding;
        applyStyle(style);
    }

    public void edit(String text, Difficulty difficulty, String explanation, List<QuestionOption> options) {
        edit(text, difficulty, explanation, options, QuestionFactory.Style.choice());
    }

    public void edit(String text, Difficulty difficulty, String explanation, List<QuestionOption> options,
                     QuestionFactory.Style style) {
        this.text = text;
        this.difficulty = difficulty;
        this.explanation = explanation;
        this.options.clear();
        this.options.addAll(options);
        applyStyle(style);
        this.updatedAt = Instant.now();
    }

    private void applyStyle(QuestionFactory.Style style) {
        this.type = style.type();
        this.codeSnippet = style.codeSnippet();
        this.showOptions = style.showOptions();
        this.acceptedAnswers.clear();
        this.acceptedAnswers.addAll(style.acceptedAnswers());
    }

    /** True when learners type their answer rather than choosing an option. */
    public boolean isTyped() {
        return type.isCode() && !showOptions;
    }

    /** Every typed answer that counts as right: the correct option's text first, then the extra accepted ones. */
    public List<String> expectedAnswers() {
        List<String> expected = new ArrayList<>();
        options.stream().filter(QuestionOption::isCorrect).findFirst().ifPresent(o -> expected.add(o.getText()));
        expected.addAll(acceptedAnswers);
        return expected;
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

    public TranscriptGrounding getGrounding() {
        return grounding;
    }

    public String getText() {
        return text;
    }

    public QuestionType getType() {
        return type;
    }

    public String getCodeSnippet() {
        return codeSnippet;
    }

    public boolean isShowOptions() {
        return showOptions;
    }

    public List<String> getAcceptedAnswers() {
        return acceptedAnswers;
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

    public boolean isFinalOnly() {
        return finalOnly;
    }

    public void setFinalOnly(boolean finalOnly) {
        this.finalOnly = finalOnly;
        this.updatedAt = Instant.now();
    }
}
