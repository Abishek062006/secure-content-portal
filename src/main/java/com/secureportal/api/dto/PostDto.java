package com.secureportal.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A feed post as one viewer sees it. {@code course} is set on promo posts (only while the course is
 * still published); {@code reactions} counts by type; {@code myReaction} is the viewer's own.
 */
public record PostDto(
        UUID id,
        String kind,
        String title,
        String body,
        String imageUrl,
        String videoUrl,
        Long authorId,
        String authorName,
        String authorHeadline,
        String authorPictureUrl,
        CertificateDto certificate,
        boolean canDelete,
        Instant publishAt,
        boolean pinned,
        boolean scheduled,
        PostCourseDto course,
        Map<String, Long> reactions,
        long reactionTotal,
        String myReaction,
        long commentCount,
        PostHackathonDto hackathon
) {
    public record PostCourseDto(UUID id, String title, String description, String category, String thumbnailUrl,
                                boolean enrolled, CourseDto.Pricing pricing) {
    }

    /** A hackathon shared to the feed: enough to show a card and link to it. */
    public record PostHackathonDto(Long id, String title, String organizer, String bannerUrl, String kind, String mode, String stream,
                                   String location, Instant eventStartDate, Instant registrationDeadline, boolean registrationOpen,
                                   String phase, String registrationUrl) {
    }

    public record CommentDto(UUID id, String userName, String userPictureUrl, String body, Instant createdAt,
                             boolean canDelete) {
    }

    public record FeedPage(java.util.List<PostDto> posts, boolean hasMore) {
    }
}
