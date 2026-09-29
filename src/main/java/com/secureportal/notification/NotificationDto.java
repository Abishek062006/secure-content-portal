package com.secureportal.notification;

import java.time.Instant;

public record NotificationDto(
        Long id,
        NotificationCategory category,
        String title,
        String message,
        NotificationPriority priority,
        boolean isRead,
        String actionUrl,
        Instant createdAt,
        Instant readAt
) {
    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getCategory(), n.getTitle(), n.getMessage(), n.getPriority(),
                n.isRead(), n.getActionUrl(), n.getCreatedAt(), n.getReadAt());
    }
}
