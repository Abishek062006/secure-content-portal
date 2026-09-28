package com.secureportal.api;

import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.notification.NotificationCategory;
import com.secureportal.notification.NotificationDto;
import com.secureportal.notification.NotificationPriority;
import com.secureportal.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationApiControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditService auditService;

    @Mock
    private AppPrincipal principal;

    @InjectMocks
    private NotificationApiController controller;

    private NotificationDto sampleDto;

    @BeforeEach
    void setUp() {
        lenient().when(principal.getUserId()).thenReturn(42L);
        lenient().when(principal.getEmail()).thenReturn("admin@example.com");

        sampleDto = new NotificationDto(
                1L, 42L, NotificationCategory.COURSE, "Course Available",
                "New Java course", NotificationPriority.NORMAL, false,
                "/courses/1", Instant.now(), null
        );
    }

    @Test
    void list_returnsPageFromService() {
        Page<NotificationDto> page = new PageImpl<>(List.of(sampleDto));
        when(notificationService.getNotifications(42L, null, false, 0, 20)).thenReturn(page);

        Page<NotificationDto> result = controller.list(null, false, 0, 20, principal);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Course Available");
    }

    @Test
    void recent_returnsRecentFromService() {
        when(notificationService.getRecentNotifications(42L, 5)).thenReturn(List.of(sampleDto));

        List<NotificationDto> result = controller.recent(principal);
        assertThat(result).hasSize(1);
    }

    @Test
    void unreadCount_returnsMap() {
        when(notificationService.getUnreadCount(42L)).thenReturn(7L);

        Map<String, Long> result = controller.unreadCount(principal);
        assertThat(result.get("unreadCount")).isEqualTo(7L);
    }

    @Test
    void markRead_whenFound_returnsDto() {
        when(notificationService.markAsRead(1L, 42L)).thenReturn(Optional.of(sampleDto));

        NotificationDto result = controller.markRead(1L, principal);
        assertThat(result).isEqualTo(sampleDto);
    }

    @Test
    void markRead_whenNotFound_throws404() {
        when(notificationService.markAsRead(99L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.markRead(99L, principal))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void markUnread_whenFound_returnsDto() {
        when(notificationService.markAsUnread(1L, 42L)).thenReturn(Optional.of(sampleDto));

        NotificationDto result = controller.markUnread(1L, principal);
        assertThat(result).isEqualTo(sampleDto);
    }

    @Test
    void markUnread_whenNotFound_throws404() {
        when(notificationService.markAsUnread(99L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.markUnread(99L, principal))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_whenSuccess_returnsNoContent() {
        when(notificationService.deleteNotification(1L, 42L)).thenReturn(true);

        ResponseEntity<Void> response = controller.delete(1L, principal);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void delete_whenNotFound_throws404() {
        when(notificationService.deleteNotification(99L, 42L)).thenReturn(false);

        assertThatThrownBy(() -> controller.delete(99L, principal))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void clearRead_returnsClearedCount() {
        when(notificationService.clearReadNotifications(42L)).thenReturn(5);

        Map<String, Integer> result = controller.clearRead(principal);
        assertThat(result.get("cleared")).isEqualTo(5);
    }

    @Test
    void clearAll_returnsClearedCount() {
        when(notificationService.clearAllNotifications(42L)).thenReturn(10);

        Map<String, Integer> result = controller.clearAll(principal);
        assertThat(result.get("cleared")).isEqualTo(10);
    }

    @Test
    void createAnnouncement_withAudienceAll_callsNotifyAllUsers() {
        var req = new NotificationApiController.AnnouncementRequest(
                "Maintenance", "Server reboot tonight", NotificationPriority.IMPORTANT,
                "/status", "ALL"
        );

        ResponseEntity<Map<String, String>> res = controller.createAnnouncement(req, principal);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).notifyAllUsers(
                NotificationCategory.ANNOUNCEMENT, "Maintenance", "Server reboot tonight",
                NotificationPriority.IMPORTANT, "/status"
        );
        verify(auditService).log(eq("admin@example.com"), eq("ANNOUNCEMENT"), isNull(), contains("ALL"));
    }

    @Test
    void createAnnouncement_withAudienceAdmins_callsNotifyAllAdmins() {
        var req = new NotificationApiController.AnnouncementRequest(
                "Admin Notice", "Review pending users", NotificationPriority.CRITICAL,
                "/admin/users", "ADMINS"
        );

        ResponseEntity<Map<String, String>> res = controller.createAnnouncement(req, principal);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).notifyAllAdmins(
                NotificationCategory.ANNOUNCEMENT, "Admin Notice", "Review pending users",
                NotificationPriority.CRITICAL, "/admin/users"
        );
    }

    @Test
    void createAnnouncement_withAudienceLearners_callsNotifyAllLearners() {
        var req = new NotificationApiController.AnnouncementRequest(
                "Learner Notice", "New quiz ready", NotificationPriority.NORMAL,
                null, "LEARNERS"
        );

        ResponseEntity<Map<String, String>> res = controller.createAnnouncement(req, principal);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).notifyAllLearners(
                NotificationCategory.ANNOUNCEMENT, "Learner Notice", "New quiz ready",
                NotificationPriority.NORMAL, null
        );
    }
}
