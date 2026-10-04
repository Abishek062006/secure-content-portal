package com.secureportal.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** The one conversation between two members. {@code user1Id} is always the smaller id, so a pair has a single canonical row. */
@Entity
@Table(name = "conversations")
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
        // for JPA
    }

    public Conversation(Long userA, Long userB) {
        this.user1Id = Math.min(userA, userB);
        this.user2Id = Math.max(userA, userB);
    }

    public void messageSentAt(Instant when) {
        this.lastMessageAt = when;
    }

    /** The member on the other side of this conversation from {@code userId}. */
    public Long otherUserId(Long userId) {
        return userId.equals(user1Id) ? user2Id : user1Id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
