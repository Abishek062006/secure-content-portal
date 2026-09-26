package com.secureportal.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name = "user_gamification")
public class UserGamification {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "total_points", nullable = false)
    private int totalPoints = 0;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak = 0;

    @Column(name = "max_streak", nullable = false)
    private int maxStreak = 0;

    @Column(name = "last_checkin_date")
    private LocalDate lastCheckinDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserGamification() {
        // JPA
    }

    public UserGamification(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public void addPoints(int amount) {
        this.totalPoints += amount;
        this.updatedAt = Instant.now();
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
        if (currentStreak > this.maxStreak) {
            this.maxStreak = currentStreak;
        }
        this.updatedAt = Instant.now();
    }

    public int getMaxStreak() {
        return maxStreak;
    }

    public void setMaxStreak(int maxStreak) {
        this.maxStreak = maxStreak;
    }

    public LocalDate getLastCheckinDate() {
        return lastCheckinDate;
    }

    public void setLastCheckinDate(LocalDate lastCheckinDate) {
        this.lastCheckinDate = lastCheckinDate;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
