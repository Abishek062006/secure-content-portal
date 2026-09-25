package com.secureportal.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One attempt as the learner sees it. While it's running the correct answers are absent (except per-answer feedback in a quiz). */
public record AttemptDto(
        UUID id,
        UUID assessmentId,
        UUID courseId,
        UUID moduleId,
        String type,
        String title,
        String status,
        Instant startedAt,
        Instant expiresAt,
        Long secondsLeft,
        boolean timedOut,
        Integer scorePercent,
        Integer correctCount,
        int totalCount,
        Boolean passed,
        Integer passPercent,
        List<QuestionView> questions
) {
    /**
     * Options are in this learner's own order; {@code selectedIndex}/{@code correctIndex} refer to that order.
     * {@code correct}, {@code correctIndex}, {@code explanation} and {@code lessonId}/{@code sourceSeconds} appear
     * only once revealed.
     */
    public record QuestionView(
            UUID id,
            String text,
            String difficulty,
            List<String> options,
            Integer selectedIndex,
            Boolean correct,
            Integer correctIndex,
            String explanation,
            UUID lessonId,
            Integer sourceSeconds
    ) {
    }
}
