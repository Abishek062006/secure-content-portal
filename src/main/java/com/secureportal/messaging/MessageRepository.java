package com.secureportal.messaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId, Pageable pageable);

    /** The latest message of each of the given conversations, in one query. */
    @Query("SELECT m FROM Message m WHERE m.id IN (SELECT MAX(m2.id) FROM Message m2 WHERE m2.conversationId IN :ids GROUP BY m2.conversationId)")
    List<Message> findLatestIn(@Param("ids") Collection<Long> ids);

    /** {conversationId, unread count} for the messages the other person sent that this member hasn't read. */
    @Query("SELECT m.conversationId, COUNT(m) FROM Message m WHERE m.conversationId IN :ids AND m.senderId <> :userId AND m.readAt IS NULL "
            + "GROUP BY m.conversationId")
    List<Object[]> unreadCounts(@Param("ids") Collection<Long> ids, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Message m SET m.readAt = :readAt WHERE m.conversationId = :conversationId AND m.senderId <> :userId AND m.readAt IS NULL")
    int markRead(@Param("conversationId") Long conversationId, @Param("userId") Long userId, @Param("readAt") Instant readAt);
}
