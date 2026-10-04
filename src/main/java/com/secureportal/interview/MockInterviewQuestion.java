package com.secureportal.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One question of an interview, with the learner's answer and the AI's assessment once it has been answered. */
@Entity
@Table(name = "mock_interview_questions")
public class MockInterviewQuestion {

    public static final int NOT_ANSWERED = -1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "question_index", nullable = false)
    private int questionIndex;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(length = 30)
    private String category;

    @Column(name = "learner_answer", columnDefinition = "TEXT")
    private String learnerAnswer;

    @Column(name = "ai_feedback", columnDefinition = "TEXT")
    private String aiFeedback;

    @Column(name = "key_strengths", columnDefinition = "TEXT")
    private String keyStrengths;

    @Column(name = "areas_to_improve", columnDefinition = "TEXT")
    private String areasToImprove;

    @Column(name = "ideal_answer", columnDefinition = "TEXT")
    private String idealAnswer;

    @Column(nullable = false)
    private int score = NOT_ANSWERED;

    @Column(name = "parent_question_id")
    private Long parentQuestionId;

    @Column(name = "relevance_score")
    private Integer relevanceScore;

    @Column(name = "depth_score")
    private Integer depthScore;

    @Column(name = "structure_score")
    private Integer structureScore;

    @Column(name = "communication_score")
    private Integer communicationScore;

    @Column(name = "answered_at")
    private Instant answeredAt;

    /** How the answer was given and, for a spoken one, its timing: all null when the browser reported nothing usable. */
    @Enumerated(EnumType.STRING)
    @Column(name = "answer_mode", length = 5)
    private AnswerMode answerMode;

    @Column(name = "thinking_seconds")
    private Integer thinkingSeconds;

    @Column(name = "speaking_seconds")
    private Integer speakingSeconds;

    @Column(name = "words_per_minute")
    private Integer wordsPerMinute;

    @Column(name = "long_pauses")
    private Integer longPauses;

    @Column(name = "longest_pause_ms")
    private Integer longestPauseMs;

    @Column(name = "audio_clear")
    private Boolean audioClear;

    protected MockInterviewQuestion() {
        // for JPA
    }

    public MockInterviewQuestion(Long sessionId, int questionIndex, String questionText, QuestionCategory category) {
        this.sessionId = sessionId;
        this.questionIndex = questionIndex;
        this.questionText = questionText;
        this.category = category.name();
    }

    /** The AI's assessment of one answer, already checked and clamped by {@link InterviewAi}. */
    public record Evaluation(int score, int relevance, int depth, int structure, int communication, String feedback, String strengths,
                             String improvements, String idealAnswer, String followUp) {
    }

    /** A follow-up question, asked because the answer to {@code parent} left something unclear. */
    public static MockInterviewQuestion followUp(Long sessionId, int questionIndex, String text, MockInterviewQuestion parent) {
        MockInterviewQuestion q = new MockInterviewQuestion(sessionId, questionIndex, text, QuestionCategory.parseOrDefault(parent.getCategory()));
        q.parentQuestionId = parent.getId();
        return q;
    }

    public boolean isFollowUp() {
        return parentQuestionId != null;
    }

    public boolean isAnswered() {
        return answeredAt != null;
    }

    public void recordAnswer(String answer, Evaluation evaluation) {
        this.learnerAnswer = answer;
        this.score = evaluation.score();
        this.aiFeedback = evaluation.feedback();
        this.keyStrengths = evaluation.strengths();
        this.areasToImprove = evaluation.improvements();
        this.idealAnswer = evaluation.idealAnswer();
        this.relevanceScore = evaluation.relevance();
        this.depthScore = evaluation.depth();
        this.structureScore = evaluation.structure();
        this.communicationScore = evaluation.communication();
        this.answeredAt = Instant.now();
    }

    /** Keeps the delivery measured for this answer. Feedback only: it plays no part in the score. */
    public void recordDelivery(AnswerDelivery.Measured delivery) {
        this.answerMode = delivery.mode();
        this.thinkingSeconds = delivery.thinkingSeconds();
        this.speakingSeconds = delivery.speakingSeconds();
        this.wordsPerMinute = delivery.wordsPerMinute();
        this.longPauses = delivery.longPauses();
        this.longestPauseMs = delivery.longestPauseMs();
        this.audioClear = delivery.audioClear();
    }

    public boolean hasDelivery() {
        return answerMode != null;
    }

    public AnswerMode getAnswerMode() {
        return answerMode;
    }

    public Integer getThinkingSeconds() {
        return thinkingSeconds;
    }

    public Integer getSpeakingSeconds() {
        return speakingSeconds;
    }

    public Integer getWordsPerMinute() {
        return wordsPerMinute;
    }

    public Integer getLongPauses() {
        return longPauses;
    }

    public Integer getLongestPauseMs() {
        return longestPauseMs;
    }

    public Boolean getAudioClear() {
        return audioClear;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getCategory() {
        return category;
    }

    public String getLearnerAnswer() {
        return learnerAnswer;
    }

    public String getAiFeedback() {
        return aiFeedback;
    }

    public String getKeyStrengths() {
        return keyStrengths;
    }

    public String getAreasToImprove() {
        return areasToImprove;
    }

    public String getIdealAnswer() {
        return idealAnswer;
    }

    public int getScore() {
        return score;
    }

    public Long getParentQuestionId() {
        return parentQuestionId;
    }

    public Integer getRelevanceScore() {
        return relevanceScore;
    }

    public Integer getDepthScore() {
        return depthScore;
    }

    public Integer getStructureScore() {
        return structureScore;
    }

    public Integer getCommunicationScore() {
        return communicationScore;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}
