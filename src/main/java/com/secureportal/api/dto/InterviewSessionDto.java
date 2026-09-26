package com.secureportal.api.dto;

import com.secureportal.interview.MockInterviewSession;

import java.time.Instant;

public record InterviewSessionDto(Long id, Long userId, String track, String stream, String difficulty, int totalQuestions,
                                  int currentQuestionIndex, int overallScore, String readinessLevel, String summaryFeedback,
                                  String status, int xpEarned, Instant createdAt, Instant completedAt) {

    public static InterviewSessionDto of(MockInterviewSession s) {
        return new InterviewSessionDto(s.getId(), s.getUserId(), s.getTrack(), s.getStream(), s.getDifficulty(), s.getTotalQuestions(),
                s.getCurrentQuestionIndex(), s.getOverallScore(), s.getReadinessLevel(), s.getSummaryFeedback(), s.getStatus(),
                s.getXpEarned(), s.getCreatedAt(), s.getCompletedAt());
    }
}
