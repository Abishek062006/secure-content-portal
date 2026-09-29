package com.secureportal.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "action_url", length = 512)
    private String actionUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // for JPA
    }

    public Notification(Long recipientUserId, NotificationCategory category, String title, String message,
                         NotificationPriority priority, String actionUrl) {
        this.recipientUserId = recipientUserId;
        this.category = category != null ? category : NotificationCategory.SYSTEM;
        this.title = title;
        this.message = message;
        this.priority = priority != null ? priority : NotificationPriority.NORMAL;
        this.actionUrl = actionUrl;
    }

    public void markAsRead() {
        this.isRead = true;
        this.readAt = Instant.now();
    }

    public void markAsUnread() {
        this.isRead = false;
        this.readAt = null;
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public NotificationPriority getPriority() {
        return priority;
    }

    public boolean isRead() {
        return isRead;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
