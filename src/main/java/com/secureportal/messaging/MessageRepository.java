package com.secureportal.messaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    Optional<Message> findTop1ByConversationIdOrderByCreatedAtDesc(Long conversationId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversationId = :conversationId AND m.senderId != :currentUserId AND m.readAt IS NULL")
    long countUnreadMessages(@Param("conversationId") Long conversationId, @Param("currentUserId") Long currentUserId);

    @Modifying
    @Query("UPDATE Message m SET m.readAt = :readAt WHERE m.conversationId = :conversationId AND m.senderId != :currentUserId AND m.readAt IS NULL")
    int markAsRead(@Param("conversationId") Long conversationId, @Param("currentUserId") Long currentUserId, @Param("readAt") Instant readAt);
}
