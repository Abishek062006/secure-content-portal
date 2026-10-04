package com.secureportal.api.dto;

import com.secureportal.interview.MockInterviewSession;

import java.time.Instant;

public record InterviewSessionDto(Long id, Long userId, String track, String stream, String difficulty, int totalQuestions,
                                  int currentQuestionIndex, int overallScore, String readinessLevel, String summaryFeedback,
                                  String status, int xpEarned, Instant createdAt, Instant completedAt, String source, String targetRole,
                                  String skills, String courseId, String topFix, String interviewType, int plannedQuestions,
                                  String interviewer, Integer faceVisiblePercent, Integer lightingGoodPercent) {

    public static InterviewSessionDto of(MockInterviewSession s) {
        return new InterviewSessionDto(s.getId(), s.getUserId(), s.getTrack(), s.getStream(), s.getDifficulty(), s.getTotalQuestions(),
                s.getCurrentQuestionIndex(), s.getOverallScore(), s.getReadinessLevel(), s.getSummaryFeedback(), s.getStatus(),
                s.getXpEarned(), s.getCreatedAt(), s.getCompletedAt(), s.getSource(), s.getTargetRole(), s.getSkills(),
                s.getCourseId(), s.getTopFix(), s.getInterviewType(), s.getPlannedQuestions(),
                s.getInterviewer().name(), s.getFaceVisiblePercent(), s.getLightingGoodPercent());
    }

    /** The same, without the camera setup measurements: those are feedback for the learner, not for admins. */
    public static InterviewSessionDto forAdmin(MockInterviewSession s) {
        InterviewSessionDto d = of(s);
        return new InterviewSessionDto(d.id(), d.userId(), d.track(), d.stream(), d.difficulty(), d.totalQuestions(),
                d.currentQuestionIndex(), d.overallScore(), d.readinessLevel(), d.summaryFeedback(), d.status(), d.xpEarned(),
                d.createdAt(), d.completedAt(), d.source(), d.targetRole(), d.skills(), d.courseId(), d.topFix(), d.interviewType(),
                d.plannedQuestions(), d.interviewer(), null, null);
    }
}
