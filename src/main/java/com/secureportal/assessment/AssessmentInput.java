package com.secureportal.assessment;

/** What an admin configures for a quiz or assessment. */
public record AssessmentInput(
        AssessmentType type,
        String title,
        int easyCount,
        int mediumCount,
        int hardCount,
        Integer passPercent,
        Integer timeLimitMinutes,
        Integer maxAttempts,
        boolean gatesNext,
        Integer reusePercent
) {
}
