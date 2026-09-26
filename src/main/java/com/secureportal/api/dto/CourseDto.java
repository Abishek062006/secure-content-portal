package com.secureportal.api.dto;

import com.secureportal.course.Course;

import java.time.Instant;
import java.util.UUID;

/**
 * One shape for learners and admins. {@code enrolled}/{@code progressPercent}
 * are only filled for learners; {@code viewCount}/{@code lastViewedAt} only for admins.
 */
public record CourseDto(
        UUID id,
        String title,
        String description,
        String category,
        String thumbnailUrl,
        String status,
        long moduleCount,
        long lessonCount,
        Instant createdAt,
        Instant updatedAt,
        Boolean enrolled,
        Integer progressPercent,
        Long viewCount,
        Instant lastViewedAt,
        Pricing pricing
) {
    /** {@code finalPriceRupees} is what a learner pays now; it differs from {@code priceRupees} while a discount is active. */
    public record Pricing(int priceRupees, boolean free, int discountPercent, Instant discountStart, Instant discountEnd,
                          boolean discountActive, int finalPriceRupees) {
        public static Pricing of(Course course) {
            var p = course.getPricing();
            Instant now = Instant.now();
            return new Pricing(p.priceRupees(), p.free(), p.discountPercent(), p.discountStart(), p.discountEnd(),
                    p.discountActive(now), p.finalPriceRupees(now));
        }
    }

    public static CourseDto forLearner(Course course, long moduleCount, long lessonCount,
                                       boolean enrolled, int progressPercent) {
        return build(course, moduleCount, lessonCount, enrolled, progressPercent, null, null);
    }

    public static CourseDto forAdmin(Course course, long moduleCount, long lessonCount) {
        return build(course, moduleCount, lessonCount, null, null, course.getViewCount(), course.getLastViewedAt());
    }

    private static CourseDto build(Course course, long moduleCount, long lessonCount, Boolean enrolled,
                                   Integer progressPercent, Long viewCount, Instant lastViewedAt) {
        String thumbnailUrl = course.getThumbnailKey() == null ? null
                : "/api/courses/" + course.getId() + "/thumbnail?v=" + course.getUpdatedAt().toEpochMilli();
        return new CourseDto(course.getId(), course.getTitle(), course.getDescription(), course.getCategory(),
                thumbnailUrl, course.getStatus().name(), moduleCount, lessonCount, course.getCreatedAt(),
                course.getUpdatedAt(), enrolled, progressPercent, viewCount, lastViewedAt, Pricing.of(course));
    }
}
