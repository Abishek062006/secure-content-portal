package com.secureportal.notification;

import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User admin;
    private User viewer;

    @BeforeEach
    void setUp() {
        admin = new User("admin@example.com", "Admin User", null, Role.ADMIN);
        viewer = new User("learner@example.com", "Learner User", null, Role.VIEWER);
    }

    @Test
    void createNotification_savesNotification() {
        Notification notification = new Notification(10L, NotificationCategory.COURSE,
                "New Course Available", "Course is published", NotificationPriority.NORMAL, "/courses/123");
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        Notification result = notificationService.createNotification(10L, NotificationCategory.COURSE,
                "New Course Available", "Course is published", NotificationPriority.NORMAL, "/courses/123");

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("New Course Available");
        assertThat(result.getRecipientUserId()).isEqualTo(10L);
        assertThat(result.getPriority()).isEqualTo(NotificationPriority.NORMAL);
        assertThat(result.isRead()).isFalse();
    }

    @Test
    void notifyAllLearners_onlyNotifiesLearners() {
        when(userRepository.findByRole(Role.VIEWER)).thenReturn(List.of(viewer));

        notificationService.notifyAllLearners(NotificationCategory.COURSE,
                "New Course Available", "Java 101 is out", NotificationPriority.NORMAL, "/courses/java");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> captured = captor.getValue();
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getTitle()).isEqualTo("New Course Available");
        assertThat(captured.get(0).getCategory()).isEqualTo(NotificationCategory.COURSE);
    }

    @Test
    void notifyAllAdmins_onlyNotifiesAdmins() {
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

        notificationService.notifyAllAdmins(NotificationCategory.CONTENT,
                "Upload Completed", "Video uploaded", NotificationPriority.NORMAL, "/admin/courses");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> captured = captor.getValue();
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getTitle()).isEqualTo("Upload Completed");
    }

    @Test
    void markAsRead_onlyMarksForCorrectUser() {
        Notification notification = new Notification(10L, NotificationCategory.QUIZ,
                "Quiz Ready", "Check your quiz", NotificationPriority.NORMAL, null);
        when(notificationRepository.findByIdAndRecipientUserId(1L, 10L)).thenReturn(Optional.of(notification));
        when(notificationRepository.findByIdAndRecipientUserId(1L, 99L)).thenReturn(Optional.empty());

        // Correct user
        Optional<NotificationDto> result = notificationService.markAsRead(1L, 10L);
        assertThat(result).isPresent();
        assertThat(result.get().isRead()).isTrue();
        verify(notificationRepository).save(notification);

        // Another user cannot mark it as read
        Optional<NotificationDto> unauthorizedResult = notificationService.markAsRead(1L, 99L);
        assertThat(unauthorizedResult).isEmpty();
    }

    @Test
    void getUnreadCount_returnsCountForLearnerCategories() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(viewer));
        when(notificationRepository.countByRecipientUserIdAndCategoryInAndIsReadFalse(eq(10L), eq(NotificationService.LEARNER_CATEGORIES)))
                .thenReturn(3L);

        long count = notificationService.getUnreadCount(10L);
        assertThat(count).isEqualTo(3L);
    }

    @Test
    void getUnreadCount_returnsCountForAdminCategories() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(notificationRepository.countByRecipientUserIdAndCategoryInAndIsReadFalse(eq(1L), eq(NotificationService.ADMIN_CATEGORIES)))
                .thenReturn(2L);

        long count = notificationService.getUnreadCount(1L);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void getNotifications_filtersCorrectlyForLearners() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(viewer));
        Notification notification = new Notification(10L, NotificationCategory.COMMUNITY,
                "New Reply", "Someone replied", NotificationPriority.NORMAL, "/feed");
        Page<Notification> page = new PageImpl<>(List.of(notification));
        when(notificationRepository.findByRecipientUserIdAndCategoryInOrderByCreatedAtDesc(eq(10L), eq(NotificationService.LEARNER_CATEGORIES), any(Pageable.class)))
                .thenReturn(page);

        Page<NotificationDto> result = notificationService.getNotifications(10L, null, false, 0, 10);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("New Reply");
    }

    @Test
    void markAsUnread_onlyMarksForCorrectUser() {
        Notification notification = new Notification(10L, NotificationCategory.QUIZ,
                "Quiz Ready", "Check your quiz", NotificationPriority.NORMAL, null);
        notification.markAsRead();
        when(notificationRepository.findByIdAndRecipientUserId(1L, 10L)).thenReturn(Optional.of(notification));
        when(notificationRepository.findByIdAndRecipientUserId(1L, 99L)).thenReturn(Optional.empty());

        // Correct user marks unread
        Optional<NotificationDto> result = notificationService.markAsUnread(1L, 10L);
        assertThat(result).isPresent();
        assertThat(result.get().isRead()).isFalse();
        verify(notificationRepository).save(notification);

        // Another user cannot mark it as unread
        Optional<NotificationDto> unauthorizedResult = notificationService.markAsUnread(1L, 99L);
        assertThat(unauthorizedResult).isEmpty();
    }

    @Test
    void deleteNotification_delegatesToRepository() {
        when(notificationRepository.deleteByIdAndRecipientUserId(5L, 10L)).thenReturn(1);
        when(notificationRepository.deleteByIdAndRecipientUserId(99L, 10L)).thenReturn(0);

        assertThat(notificationService.deleteNotification(5L, 10L)).isTrue();
        assertThat(notificationService.deleteNotification(99L, 10L)).isFalse();
    }

    @Test
    void clearReadNotifications_delegatesToRepository() {
        when(notificationRepository.deleteByRecipientUserIdAndIsReadTrue(10L)).thenReturn(4);
        int cleared = notificationService.clearReadNotifications(10L);
        assertThat(cleared).isEqualTo(4);
    }

    @Test
    void clearAllNotifications_delegatesToRepository() {
        when(notificationRepository.deleteByRecipientUserId(10L)).thenReturn(8);
        int cleared = notificationService.clearAllNotifications(10L);
        assertThat(cleared).isEqualTo(8);
    }
}
