package com.secureportal.messaging;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** The ids are given smallest first, matching how a conversation is stored. */
    Optional<Conversation> findByUser1IdAndUser2Id(Long user1Id, Long user2Id);

    /** Scoped to a participant, so someone else's conversation id never resolves. */
    @Query("SELECT c FROM Conversation c WHERE c.id = :id AND (c.user1Id = :userId OR c.user2Id = :userId)")
    Optional<Conversation> findForParticipant(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT c FROM Conversation c WHERE c.user1Id = :userId OR c.user2Id = :userId ORDER BY c.lastMessageAt DESC, c.id DESC")
    List<Conversation> findRecentForUser(@Param("userId") Long userId, Pageable page);
}
