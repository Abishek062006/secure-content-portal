package com.secureportal.notification;

import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class NotificationService {

    public static final Set<NotificationCategory> ADMIN_CATEGORIES = Set.of(
            NotificationCategory.COURSE,
            NotificationCategory.QUIZ,
            NotificationCategory.ANNOUNCEMENT,
            NotificationCategory.COMMUNITY,
            NotificationCategory.ACHIEVEMENT,
            NotificationCategory.SECURITY
    );

    public static final Set<NotificationCategory> LEARNER_CATEGORIES = Set.of(
            NotificationCategory.COURSE,
            NotificationCategory.QUIZ,
            NotificationCategory.COMMUNITY,
            NotificationCategory.ANNOUNCEMENT,
            NotificationCategory.ACHIEVEMENT,
            NotificationCategory.SECURITY
    );

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void createNotification(Long recipientUserId, NotificationCategory category, String title, String message,
                                    NotificationPriority priority, String actionUrl) {
        notificationRepository.save(new Notification(recipientUserId, category, title, message, priority, actionUrl));
    }

    @Transactional
    public void notifyAllLearners(NotificationCategory category, String title, String message,
                                   NotificationPriority priority, String actionUrl) {
        notifyUsers(userRepository.findByRole(Role.VIEWER), category, title, message, priority, actionUrl);
    }

    @Transactional
    public void notifyAllAdmins(NotificationCategory category, String title, String message,
                                 NotificationPriority priority, String actionUrl) {
        notifyUsers(userRepository.findByRole(Role.ADMIN), category, title, message, priority, actionUrl);
    }

    private void notifyUsers(List<User> recipients, NotificationCategory category, String title, String message,
                              NotificationPriority priority, String actionUrl) {
        if (recipients.isEmpty()) {
            return;
        }
        List<Notification> batch = new ArrayList<>(recipients.size());
        for (User recipient : recipients) {
            batch.add(new Notification(recipient.getId(), category, title, message, priority, actionUrl));
        }
        notificationRepository.saveAll(batch);
    }

    private Set<NotificationCategory> allowedCategories(Long userId) {
        return userRepository.findById(userId).filter(User::isAdmin).isPresent() ? ADMIN_CATEGORIES : LEARNER_CATEGORIES;
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotifications(Long userId, NotificationCategory category, boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Set<NotificationCategory> allowed = allowedCategories(userId);

        Page<Notification> result;
        if (category != null) {
            if (!allowed.contains(category)) {
                return Page.empty(pageable);
            }
            result = unreadOnly
                    ? notificationRepository.findByRecipientUserIdAndCategoryInAndIsReadFalseOrderByCreatedAtDesc(userId, Set.of(category), pageable)
                    : notificationRepository.findByRecipientUserIdAndCategoryOrderByCreatedAtDesc(userId, category, pageable);
        } else {
            result = unreadOnly
                    ? notificationRepository.findByRecipientUserIdAndCategoryInAndIsReadFalseOrderByCreatedAtDesc(userId, allowed, pageable)
                    : notificationRepository.findByRecipientUserIdAndCategoryInOrderByCreatedAtDesc(userId, allowed, pageable);
        }
        return result.map(NotificationDto::from);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getRecentNotifications(Long userId, int limit) {
        List<Notification> list = notificationRepository.findTop10ByRecipientUserIdAndCategoryInOrderByCreatedAtDesc(userId, allowedCategories(userId));
        return list.stream().limit(limit).map(NotificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientUserIdAndCategoryInAndIsReadFalse(userId, allowedCategories(userId));
    }

    @Transactional
    public NotificationDto markAsRead(Long notificationId, Long userId) {
        Notification n = notificationRepository.findByIdAndRecipientUserId(notificationId, userId)
                .orElseThrow(NotificationNotFoundException::new);
        if (!n.isRead()) {
            n.markAsRead();
        }
        return NotificationDto.from(n);
    }

    @Transactional
    public NotificationDto markAsUnread(Long notificationId, Long userId) {
        Notification n = notificationRepository.findByIdAndRecipientUserId(notificationId, userId)
                .orElseThrow(NotificationNotFoundException::new);
        if (n.isRead()) {
            n.markAsUnread();
        }
        return NotificationDto.from(n);
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId, Instant.now());
    }

    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        if (notificationRepository.deleteByIdAndRecipientUserId(notificationId, userId) == 0) {
            throw new NotificationNotFoundException();
        }
    }

    @Transactional
    public int clearReadNotifications(Long userId) {
        return notificationRepository.deleteByRecipientUserIdAndIsReadTrue(userId);
    }
}
