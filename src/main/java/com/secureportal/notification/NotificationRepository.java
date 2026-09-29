package com.secureportal.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserIdAndCategoryOrderByCreatedAtDesc(Long recipientUserId, NotificationCategory category, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndCategoryInOrderByCreatedAtDesc(Long recipientUserId, Collection<NotificationCategory> categories, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndCategoryInAndIsReadFalseOrderByCreatedAtDesc(Long recipientUserId, Collection<NotificationCategory> categories, Pageable pageable);

    List<Notification> findTop10ByRecipientUserIdAndCategoryInOrderByCreatedAtDesc(Long recipientUserId, Collection<NotificationCategory> categories);

    long countByRecipientUserIdAndCategoryInAndIsReadFalse(Long recipientUserId, Collection<NotificationCategory> categories);

    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.recipientUserId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId, @Param("readAt") Instant readAt);

    @Modifying
    int deleteByIdAndRecipientUserId(Long id, Long recipientUserId);

    @Modifying
    int deleteByRecipientUserIdAndIsReadTrue(Long recipientUserId);
}
