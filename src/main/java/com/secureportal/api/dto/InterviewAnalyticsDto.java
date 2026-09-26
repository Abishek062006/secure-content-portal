package com.secureportal.api.dto;

import com.secureportal.interview.MockInterviewService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The admin's overview of all mock interviews. */
public record InterviewAnalyticsDto(long totalSessions, long completedSessions, long averageScore, Map<String, Long> trackDistribution,
                                    Map<String, Long> difficultyDistribution, Map<String, Integer> streamAverageScores,
                                    List<Recent> recentSessions) {

    public record Recent(Long id, Long userId, String track, String stream, String difficulty, int overallScore, String readinessLevel,
                         String status, Instant createdAt, String candidateName, String candidateEmail) {
    }

    public static InterviewAnalyticsDto of(MockInterviewService.Analytics a) {
        List<Recent> recent = a.recent().stream().map(r -> new Recent(r.session().getId(), r.session().getUserId(), r.session().getTrack(),
                r.session().getStream(), r.session().getDifficulty(), r.session().getOverallScore(), r.session().getReadinessLevel(),
                r.session().getStatus(), r.session().getCreatedAt(), r.candidateName(), r.candidateEmail())).toList();
        return new InterviewAnalyticsDto(a.totalSessions(), a.completedSessions(), a.averageScore(), a.byTrack(), a.byDifficulty(),
                a.averageByStream(), recent);
    }
}
