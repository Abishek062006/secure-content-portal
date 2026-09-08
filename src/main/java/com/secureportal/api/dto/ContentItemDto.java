package com.secureportal.api.dto;

import com.secureportal.content.ContentItem;

import java.time.Instant;
import java.util.UUID;

/**
 * Deliberately doesn't include {@code uploadedBy} — that's a lazy-loaded
 * association, and touching it here would need the JPA session still open
 * (open-in-view is off), plus the frontend has no real use for it.
 */
public record ContentItemDto(
        UUID id,
        String title,
        String description,
        String category,
        String contentType,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String sizeLabel,
        Integer pageCount,
        long viewCount,
        Instant lastViewedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ContentItemDto from(ContentItem item) {
        return new ContentItemDto(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getCategory(),
                item.getContentType().name(),
                item.getOriginalFilename(),
                item.getMimeType(),
                item.getSizeBytes(),
                item.getSizeLabel(),
                item.getPageCount(),
                item.getViewCount(),
                item.getLastViewedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
