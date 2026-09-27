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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

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
    public Page<NotificationDto> list(
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly,
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
        long count = notificationService.getUnreadCount(principal.getUserId());
        return Map.of("unreadCount", count);
    }

    @PutMapping("/notifications/{id}/read")
    public NotificationDto markRead(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        return notificationService.markAsRead(id, principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    @PutMapping("/notifications/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AppPrincipal principal) {
        int updated = notificationService.markAllAsRead(principal.getUserId());
        return Map.of("markedRead", updated);
    }

    public record AnnouncementRequest(
            @NotBlank(message = "Title is required") @Size(max = 200, message = "Title must be 200 characters or fewer") String title,
            @NotBlank(message = "Message is required") String message,
            NotificationPriority priority,
            String actionUrl) {
    }

    @PostMapping("/admin/notifications/announcement")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> createAnnouncement(
            @Valid @RequestBody AnnouncementRequest request,
            @AuthenticationPrincipal AppPrincipal principal) {
        String cleanActionUrl = (request.actionUrl() != null && !request.actionUrl().isBlank())
                ? request.actionUrl().trim() : null;
        notificationService.notifyAllLearners(
                NotificationCategory.ANNOUNCEMENT,
                request.title().trim(),
                request.message().trim(),
                request.priority() != null ? request.priority() : NotificationPriority.IMPORTANT,
                cleanActionUrl
        );
        try {
            if (principal != null && principal.getEmail() != null) {
                auditService.log(principal.getEmail(), "ANNOUNCEMENT", null, "\"" + request.title().trim() + "\"");
            }
        } catch (Exception ignored) {
            // Non-blocking
        }
        return ResponseEntity.ok(Map.of("status", "success", "message", "Announcement sent to all users"));
    }
}
