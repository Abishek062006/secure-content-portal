package com.secureportal.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("SELECT c FROM Conversation c WHERE (c.user1Id = :userA AND c.user2Id = :userB) OR (c.user1Id = :userB AND c.user2Id = :userA)")
    Optional<Conversation> findConversationBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    @Query("SELECT c FROM Conversation c WHERE c.user1Id = :userId OR c.user2Id = :userId ORDER BY c.lastMessageAt DESC")
    List<Conversation> findAllForUser(@Param("userId") Long userId);
}
