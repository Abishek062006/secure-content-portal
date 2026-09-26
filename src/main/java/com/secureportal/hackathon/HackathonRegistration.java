package com.secureportal.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A learner's registration for a hackathon. There is at most one per learner and hackathon (unique in the database). */
@Entity
@Table(name = "hackathon_registrations")
public class HackathonRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hackathon_id", nullable = false)
    private Long hackathonId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "points_claimed", nullable = false)
    private int pointsClaimed;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt = Instant.now();

    protected HackathonRegistration() {
        // for JPA
    }

    public Long getHackathonId() {
        return hackathonId;
    }

    public Long getUserId() {
        return userId;
    }

    public int getPointsClaimed() {
        return pointsClaimed;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
