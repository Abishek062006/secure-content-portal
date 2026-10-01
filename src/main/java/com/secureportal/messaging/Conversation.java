package com.secureportal.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
    name = "conversations",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_conversations_user_pair", columnNames = {"user1_id", "user2_id"})
    }
)
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user1_id", nullable = false)
    private Long user1Id;

    @Column(name = "user2_id", nullable = false)
    private Long user2Id;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt = Instant.now();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Conversation() {
        // JPA
    }

    public Conversation(Long userA, Long userB) {
        // Normalize user IDs so user1Id < user2Id for canonical uniqueness
        if (userA < userB) {
            this.user1Id = userA;
            this.user2Id = userB;
        } else {
            this.user1Id = userB;
            this.user2Id = userA;
        }
        this.createdAt = Instant.now();
        this.lastMessageAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUser1Id() {
        return user1Id;
    }

    public Long getUser2Id() {
        return user2Id;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getOtherUserId(Long myId) {
        return myId.equals(user1Id) ? user2Id : user1Id;
    }
}
