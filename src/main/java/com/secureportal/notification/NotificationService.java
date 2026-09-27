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
import java.util.Optional;
import java.util.Set;

@Service
public class NotificationService {

    public static final Set<NotificationCategory> ADMIN_CATEGORIES = Set.of(
            NotificationCategory.CONTENT,
            NotificationCategory.MODERATION,
            NotificationCategory.SECURITY,
            NotificationCategory.SYSTEM
    );

    public static final Set<NotificationCategory> LEARNER_CATEGORIES = Set.of(
            NotificationCategory.COURSE,
            NotificationCategory.QUIZ,
            NotificationCategory.COMMUNITY,
            NotificationCategory.ANNOUNCEMENT
    );

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Notification createNotification(Long recipientUserId, NotificationCategory category,
                                           String title, String message, NotificationPriority priority,
                                           String actionUrl) {
        if (recipientUserId == null || title == null || message == null) {
            return null;
        }
        Notification notification = new Notification(
                recipientUserId,
                category != null ? category : NotificationCategory.SYSTEM,
                title.trim(),
                message.trim(),
                priority != null ? priority : NotificationPriority.NORMAL,
                actionUrl
        );
        return notificationRepository.save(notification);
    }

    @Transactional
    public void notifyAllLearners(NotificationCategory category, String title, String message,
                                 NotificationPriority priority, String actionUrl) {
        List<User> learners = userRepository.findByRole(Role.VIEWER);
        List<Notification> batch = new ArrayList<>(learners.size());
        for (User u : learners) {
            batch.add(new Notification(u.getId(), category, title, message, priority, actionUrl));
        }
        if (!batch.isEmpty()) {
            notificationRepository.saveAll(batch);
        }
    }

    @Transactional
    public void notifyAllAdmins(NotificationCategory category, String title, String message,
                                NotificationPriority priority, String actionUrl) {
        List<User> admins = userRepository.findByRole(Role.ADMIN);
        List<Notification> batch = new ArrayList<>(admins.size());
        for (User admin : admins) {
            batch.add(new Notification(admin.getId(), category, title, message, priority, actionUrl));
        }
        if (!batch.isEmpty()) {
            notificationRepository.saveAll(batch);
        }
    }

    @Transactional
    public void notifyAllUsers(NotificationCategory category, String title, String message,
                               NotificationPriority priority, String actionUrl) {
        List<User> users = userRepository.findAll();
        List<Notification> batch = new ArrayList<>(users.size());
        for (User u : users) {
            batch.add(new Notification(u.getId(), category, title, message, priority, actionUrl));
        }
        if (!batch.isEmpty()) {
            notificationRepository.saveAll(batch);
        }
    }

    @Transactional
    public void notifyUsers(Iterable<Long> userIds, NotificationCategory category, String title,
                            String message, NotificationPriority priority, String actionUrl) {
        List<Notification> batch = new ArrayList<>();
        for (Long uid : userIds) {
            if (uid != null) {
                batch.add(new Notification(uid, category, title, message, priority, actionUrl));
            }
        }
        if (!batch.isEmpty()) {
            notificationRepository.saveAll(batch);
        }
    }

    private Set<NotificationCategory> resolveAllowedCategories(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.isAdmin()) {
            return ADMIN_CATEGORIES;
        }
        return LEARNER_CATEGORIES;
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotifications(Long userId, NotificationCategory category,
                                                 Boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Set<NotificationCategory> allowedCategories = resolveAllowedCategories(userId);

        Page<Notification> result;

        if (category != null) {
            if (!allowedCategories.contains(category)) {
                return Page.empty(pageable);
            }
            if (Boolean.TRUE.equals(unreadOnly)) {
                result = notificationRepository.findByRecipientUserIdAndCategoryInAndIsReadFalseOrderByCreatedAtDesc(
                        userId, Set.of(category), pageable);
            } else {
                result = notificationRepository.findByRecipientUserIdAndCategoryOrderByCreatedAtDesc(userId, category, pageable);
            }
        } else if (Boolean.TRUE.equals(unreadOnly)) {
            result = notificationRepository.findByRecipientUserIdAndCategoryInAndIsReadFalseOrderByCreatedAtDesc(
                    userId, allowedCategories, pageable);
        } else {
            result = notificationRepository.findByRecipientUserIdAndCategoryInOrderByCreatedAtDesc(
                    userId, allowedCategories, pageable);
        }
        return result.map(NotificationDto::from);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> getRecentNotifications(Long userId, int limit) {
        Set<NotificationCategory> allowedCategories = resolveAllowedCategories(userId);
        List<Notification> list = notificationRepository.findTop10ByRecipientUserIdAndCategoryInOrderByCreatedAtDesc(
                userId, allowedCategories);
        int take = Math.min(list.size(), Math.max(1, limit));
        return list.subList(0, take).stream().map(NotificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        Set<NotificationCategory> allowedCategories = resolveAllowedCategories(userId);
        return notificationRepository.countByRecipientUserIdAndCategoryInAndIsReadFalse(userId, allowedCategories);
    }

    @Transactional
    public Optional<NotificationDto> markAsRead(Long notificationId, Long userId) {
        Optional<Notification> opt = notificationRepository.findByIdAndRecipientUserId(notificationId, userId);
        if (opt.isPresent()) {
            Notification n = opt.get();
            if (!n.isRead()) {
                n.markAsRead();
                notificationRepository.save(n);
            }
            return Optional.of(NotificationDto.from(n));
        }
        return Optional.empty();
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId, Instant.now());
    }
}
