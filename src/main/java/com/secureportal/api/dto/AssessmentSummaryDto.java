package com.secureportal.api.dto;

import java.util.UUID;

/**
 * A quiz or assessment as configured, plus — depending on who's asking — the learner's own standing
 * ({@code status} .. {@code inProgressAttemptId}) or how many approved questions are available
 * ({@code available*}, admin only). Fields that don't apply to the viewer are null.
 */
public record AssessmentSummaryDto(
        UUID id,
        UUID moduleId,
        String type,
        String title,
        int easyCount,
        int mediumCount,
        int hardCount,
        int questionCount,
        Integer passPercent,
        Integer timeLimitMinutes,
        Integer maxAttempts,
        boolean gatesNext,
        String status,
        String lockedReason,
        Integer attemptsUsed,
        Integer attemptsLeft,
        Integer bestScore,
        Boolean passed,
        UUID inProgressAttemptId,
        Integer availableEasy,
        Integer availableMedium,
        Integer availableHard,
        Integer reusePercent,
        Integer newEasy,
        Integer newMedium,
        Integer newHard
) {
}
