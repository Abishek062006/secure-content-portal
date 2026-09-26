package com.secureportal.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One mock interview a learner sits: a track, a stream and a difficulty, and a fixed set of questions. */
@Entity
@Table(name = "mock_interview_sessions")
public class MockInterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String track;

    @Column(nullable = false, length = 100)
    private String stream;

    @Column(nullable = false, length = 10)
    private String difficulty;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "current_question_index", nullable = false)
    private int currentQuestionIndex;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Column(name = "readiness_level", nullable = false, length = 20)
    private String readinessLevel = "PENDING";

    @Column(name = "summary_feedback", columnDefinition = "TEXT")
    private String summaryFeedback;

    @Column(nullable = false, length = 20)
    private String status = InterviewStatus.IN_PROGRESS.name();

    @Column(name = "xp_earned", nullable = false)
    private int xpEarned;

    @Column(nullable = false, length = 12)
    private String source = InterviewSource.SKILLS.name();

    @Column(name = "target_role", length = 100)
    private String targetRole;

    @Column(length = 600)
    private String skills;

    @Column(name = "job_description", columnDefinition = "TEXT")
    private String jobDescription;

    @Column(name = "course_id", length = 36)
    private String courseId;

    @Column(name = "top_fix", columnDefinition = "TEXT")
    private String topFix;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected MockInterviewSession() {
        // for JPA
    }

    public MockInterviewSession(Long userId, InterviewTrack track, String stream, InterviewDifficulty difficulty, int totalQuestions) {
        this.userId = userId;
        this.track = track.name();
        this.stream = stream;
        this.difficulty = difficulty.name();
        this.totalQuestions = totalQuestions;
    }

    /** What the interview was built from: the goal the learner set, kept so they can practise again with the same one. */
    public record Goal(InterviewSource source, String targetRole, String skills, String jobDescription, String courseId) {
    }

    public MockInterviewSession(Long userId, InterviewTrack track, InterviewDifficulty difficulty, Goal goal, int totalQuestions) {
        this(userId, track, goal.targetRole(), difficulty, totalQuestions);
        this.source = goal.source().name();
        this.targetRole = goal.targetRole();
        this.skills = goal.skills();
        this.jobDescription = goal.jobDescription();
        this.courseId = goal.courseId();
    }

    public Goal goal() {
        return new Goal(InterviewSource.valueOf(source), targetRole, skills, jobDescription, courseId);
    }

    /** A follow-up adds a question to the interview. */
    public void followUpAdded() {
        totalQuestions++;
    }

    public boolean isInProgress() {
        return InterviewStatus.IN_PROGRESS.name().equals(status);
    }

    public boolean isCompleted() {
        return InterviewStatus.COMPLETED.name().equals(status);
    }

    public boolean isOwnedBy(Long learnerId) {
        return userId.equals(learnerId);
    }

    public void questionAnswered() {
        currentQuestionIndex++;
    }

    public void complete(int score, String readiness, String summary, String topFix, int xp) {
        this.overallScore = score;
        this.readinessLevel = readiness;
        this.summaryFeedback = summary;
        this.topFix = topFix;
        this.xpEarned = xp;
        this.status = InterviewStatus.COMPLETED.name();
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTrack() {
        return track;
    }

    public String getStream() {
        return stream;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    public int getOverallScore() {
        return overallScore;
    }

    public String getReadinessLevel() {
        return readinessLevel;
    }

    public String getSummaryFeedback() {
        return summaryFeedback;
    }

    public String getSource() {
        return source;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public String getSkills() {
        return skills;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getTopFix() {
        return topFix;
    }

    public String getStatus() {
        return status;
    }

    public int getXpEarned() {
        return xpEarned;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
