package com.secureportal.interview;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mock_interview_questions")
public class MockInterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    private int questionIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String questionText;

    private String category; // "TECHNICAL", "SYSTEM_DESIGN", "BEHAVIORAL", "PROBLEM_SOLVING"

    @Column(columnDefinition = "TEXT")
    private String learnerAnswer;

    @Column(columnDefinition = "TEXT")
    private String aiFeedback;

    @Column(columnDefinition = "TEXT")
    private String keyStrengths;

    @Column(columnDefinition = "TEXT")
    private String areasToImprove;

    @Column(columnDefinition = "TEXT")
    private String idealAnswer;

    private int score = -1; // -1 if un-answered, 0 - 10 once answered

    private Instant answeredAt;

    public MockInterviewQuestion() {}

    public MockInterviewQuestion(Long sessionId, int questionIndex, String questionText, String category) {
        this.sessionId = sessionId;
        this.questionIndex = questionIndex;
        this.questionText = questionText;
        this.category = category;
        this.score = -1;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public int getQuestionIndex() { return questionIndex; }
    public void setQuestionIndex(int questionIndex) { this.questionIndex = questionIndex; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getLearnerAnswer() { return learnerAnswer; }
    public void setLearnerAnswer(String learnerAnswer) { this.learnerAnswer = learnerAnswer; }

    public String getAiFeedback() { return aiFeedback; }
    public void setAiFeedback(String aiFeedback) { this.aiFeedback = aiFeedback; }

    public String getKeyStrengths() { return keyStrengths; }
    public void setKeyStrengths(String keyStrengths) { this.keyStrengths = keyStrengths; }

    public String getAreasToImprove() { return areasToImprove; }
    public void setAreasToImprove(String areasToImprove) { this.areasToImprove = areasToImprove; }

    public String getIdealAnswer() { return idealAnswer; }
    public void setIdealAnswer(String idealAnswer) { this.idealAnswer = idealAnswer; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }
}
