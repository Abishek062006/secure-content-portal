package com.secureportal.interview;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mock_interview_sessions")
public class MockInterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String track; // "STUDENT", "WORKING_PROFESSIONAL"

    @Column(nullable = false)
    private String stream; // "Engineering & Web Dev", "AI & Data Science", etc.

    @Column(nullable = false)
    private String difficulty; // "EASY", "MEDIUM", "HARD"

    private int totalQuestions = 3;
    private int currentQuestionIndex = 0;

    private int overallScore = 0; // 0 - 100
    private String readinessLevel = "PENDING"; // "EXCELLENT", "GOOD", "NEEDS_PRACTICE", "PENDING"

    @Column(columnDefinition = "TEXT")
    private String summaryFeedback;

    @Column(nullable = false)
    private String status = "IN_PROGRESS"; // "IN_PROGRESS", "COMPLETED", "ABANDONED"

    private int xpEarned = 50;

    private Instant createdAt = Instant.now();
    private Instant completedAt;

    public MockInterviewSession() {}

    public MockInterviewSession(Long userId, String track, String stream, String difficulty, int totalQuestions) {
        this.userId = userId;
        this.track = track;
        this.stream = stream;
        this.difficulty = difficulty;
        this.totalQuestions = totalQuestions;
        this.currentQuestionIndex = 0;
        this.status = "IN_PROGRESS";
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTrack() { return track; }
    public void setTrack(String track) { this.track = track; }

    public String getStream() { return stream; }
    public void setStream(String stream) { this.stream = stream; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }

    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public void setCurrentQuestionIndex(int currentQuestionIndex) { this.currentQuestionIndex = currentQuestionIndex; }

    public int getOverallScore() { return overallScore; }
    public void setOverallScore(int overallScore) { this.overallScore = overallScore; }

    public String getReadinessLevel() { return readinessLevel; }
    public void setReadinessLevel(String readinessLevel) { this.readinessLevel = readinessLevel; }

    public String getSummaryFeedback() { return summaryFeedback; }
    public void setSummaryFeedback(String summaryFeedback) { this.summaryFeedback = summaryFeedback; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getXpEarned() { return xpEarned; }
    public void setXpEarned(int xpEarned) { this.xpEarned = xpEarned; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
