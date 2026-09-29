package com.secureportal.api;

import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.notification.NotificationCategory;
import com.secureportal.notification.NotificationDto;
import com.secureportal.notification.NotificationPriority;
import com.secureportal.notification.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** A learner's or admin's own notification feed, plus the admin broadcast-announcement action. */
@RestController
@RequestMapping("/api")
public class NotificationApiController {

    private final NotificationService notificationService;
    private final AuditService auditService;

    public NotificationApiController(NotificationService notificationService, AuditService auditService) {
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @GetMapping("/notifications")
    public Page<NotificationDto> list(@RequestParam(required = false) NotificationCategory category,
                                       @RequestParam(defaultValue = "false") boolean unreadOnly,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @AuthenticationPrincipal AppPrincipal principal) {
        return notificationService.getNotifications(principal.getUserId(), category, unreadOnly, page, size);
    }

    @GetMapping("/notifications/recent")
    public List<NotificationDto> recent(@AuthenticationPrincipal AppPrincipal principal) {
        return notificationService.getRecentNotifications(principal.getUserId(), 5);
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AppPrincipal principal) {
        return Map.of("unreadCount", notificationService.getUnreadCount(principal.getUserId()));
    }

    @PutMapping("/notifications/{id}/read")
    public NotificationDto markRead(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        return notificationService.markAsRead(id, principal.getUserId());
    }

    @PutMapping("/notifications/{id}/unread")
    public NotificationDto markUnread(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        return notificationService.markAsUnread(id, principal.getUserId());
    }

    @PutMapping("/notifications/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AppPrincipal principal) {
        return Map.of("markedRead", notificationService.markAllAsRead(principal.getUserId()));
    }

    @DeleteMapping("/notifications/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        notificationService.deleteNotification(id, principal.getUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/notifications/clear-read")
    public Map<String, Integer> clearRead(@AuthenticationPrincipal AppPrincipal principal) {
        return Map.of("cleared", notificationService.clearReadNotifications(principal.getUserId()));
    }

    public record AnnouncementRequest(
            @NotBlank(message = "Title is required") @Size(max = 200, message = "Title must be 200 characters or fewer") String title,
            @NotBlank(message = "Message is required") String message,
            NotificationPriority priority,
            String actionUrl,
            String targetAudience) {
    }

    @PostMapping("/admin/notifications/announcement")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> createAnnouncement(@Valid @RequestBody AnnouncementRequest request,
                                                                    @AuthenticationPrincipal AppPrincipal principal) {
        String actionUrl = (request.actionUrl() != null && !request.actionUrl().isBlank()) ? request.actionUrl().trim() : null;
        NotificationPriority priority = request.priority() != null ? request.priority() : NotificationPriority.IMPORTANT;
        String audience = request.targetAudience() != null ? request.targetAudience().trim().toUpperCase() : "ALL";
        String title = request.title().trim();
        String message = request.message().trim();

        if (!"LEARNERS".equals(audience)) {
            notificationService.notifyAllAdmins(NotificationCategory.ANNOUNCEMENT, title, message, priority, actionUrl);
        }
        if (!"ADMINS".equals(audience)) {
            notificationService.notifyAllLearners(NotificationCategory.ANNOUNCEMENT, title, message, priority, actionUrl);
        }

        auditService.log(principal.getEmail(), "ANNOUNCEMENT", null, "\"" + title + "\" [Audience: " + audience + "]");
        return ResponseEntity.ok(Map.of("status", "success", "message", "Announcement broadcast successfully (" + audience + ")"));
    }
}
