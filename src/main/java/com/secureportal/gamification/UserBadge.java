package com.secureportal.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_badges")
public class UserBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "badge_id", nullable = false)
    private String badgeId;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt = Instant.now();

    protected UserBadge() {
        // JPA
    }

    public UserBadge(Long userId, String badgeId) {
        this.userId = userId;
        this.badgeId = badgeId;
        this.unlockedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getBadgeId() {
        return badgeId;
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }
}
