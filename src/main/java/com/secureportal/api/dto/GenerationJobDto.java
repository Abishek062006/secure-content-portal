package com.secureportal.api.dto;

import com.secureportal.jobs.GenerationJob;

import java.time.Instant;
import java.util.UUID;

/** Where a background question-generation job is: {@code produced} of {@code requested} so far. */
public record GenerationJobDto(UUID id, UUID courseId, UUID lessonId, String status, int requested, int produced,
                               String difficulty, boolean finalOnly, String message, Instant createdAt) {
    public static GenerationJobDto of(GenerationJob j) {
        return new GenerationJobDto(j.getId(), j.getCourseId(), j.getLessonId(), j.getStatus().name(), j.getRequestedCount(),
                j.getProduced(), j.getDifficulty() == null ? null : j.getDifficulty().name(), j.isFinalOnly(), j.getMessage(),
                j.getCreatedAt());
    }
}
