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
        String body,
        String imageUrl,
        String authorName,
        String authorPictureUrl,
        Instant publishAt,
        boolean pinned,
        boolean scheduled,
        PostCourseDto course,
        Map<String, Long> reactions,
        long reactionTotal,
        String myReaction,
        long commentCount
) {
    public record PostCourseDto(UUID id, String title, String description, String category, String thumbnailUrl,
                                boolean enrolled) {
    }

    public record CommentDto(UUID id, String userName, String userPictureUrl, String body, Instant createdAt,
                             boolean canDelete) {
    }

    public record FeedPage(java.util.List<PostDto> posts, boolean hasMore) {
    }
}
