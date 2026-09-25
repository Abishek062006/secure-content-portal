package com.secureportal.api.dto;

import com.secureportal.course.Course;

import java.time.Instant;
import java.util.UUID;

/**
 * One shape for viewers and admins. {@code viewCount}/{@code lastViewedAt}
 * are only filled by {@link #forAdmin}, since view stats are admin-only.
 */
public record CourseDto(
        UUID id,
        String title,
        String description,
        String category,
        String thumbnailUrl,
        boolean hasTranscript,
        String videoSizeLabel,
        Instant createdAt,
        Instant updatedAt,
        String videoFilename,
        String transcriptFilename,
        Long viewCount,
        Instant lastViewedAt
) {
    public static CourseDto forViewer(Course course) {
        return build(course, false);
    }

    public static CourseDto forAdmin(Course course) {
        return build(course, true);
    }

    private static CourseDto build(Course course, boolean admin) {
        String thumbnailUrl = course.getThumbnailKey() == null ? null
                : "/api/courses/" + course.getId() + "/thumbnail?v=" + course.getUpdatedAt().toEpochMilli();
        return new CourseDto(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getCategory(),
                thumbnailUrl,
                course.getTranscriptKey() != null,
                course.getVideoSizeLabel(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                admin ? course.getVideoFilename() : null,
                admin ? course.getTranscriptFilename() : null,
                admin ? course.getViewCount() : null,
                admin ? course.getLastViewedAt() : null
        );
    }
}
