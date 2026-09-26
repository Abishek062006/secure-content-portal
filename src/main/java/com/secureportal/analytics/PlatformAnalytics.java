package com.secureportal.analytics;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything the admin analytics page shows, computed in one pass. */
public record PlatformAnalytics(
        Totals totals,
        Funnel funnel,
        Quiz quiz,
        Content content,
        Community community,
        List<CourseRow> topCourses,
        Series series,
        List<Activity> recent
) implements Serializable {
    /** {@code new30} figures count the last 30 days, {@code active7} the last 7. */
    public record Totals(long learners, long newLearners30, long activeLearners7, long enrollments, long enrollments30,
                         long publishedCourses, long draftCourses, long certificates, long certificates30) implements Serializable {
    }

    /** Learners' progress through courses: signed up, made a start, finished every lesson, earned the certificate. */
    public record Funnel(long enrolled, long started, long completed, long certified) implements Serializable {
    }

    public record Quiz(long attempts, long attempts30, Double averageScore, Double passRate) implements Serializable {
    }

    public record Content(long lessons, long materials, long questions, long approvedQuestions, long aiQuestions) implements Serializable {
    }

    public record Community(long posts, long posts30, long comments, long reactions, Map<String, Long> reactionsByType) implements Serializable {
    }

    public record CourseRow(UUID id, String title, String category, long enrollments, long completed, long certificates) implements Serializable {
    }

    /** One entry per day for the last 30 days, oldest first. */
    public record Series(List<String> dates, List<Integer> enrollments, List<Integer> newLearners, List<Integer> attempts,
                         List<Integer> posts) implements Serializable {
    }

    public record Activity(String actor, String action, String detail, Instant at) implements Serializable {
    }
}
