package com.secureportal.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A learner's saved hackathon. There is at most one per learner and hackathon (unique in the database). */
@Entity
@Table(name = "hackathon_saves")
public class HackathonSave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hackathon_id", nullable = false)
    private Long hackathonId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt = Instant.now();

    protected HackathonSave() {
        // for JPA
    }

    public Long getHackathonId() {
        return hackathonId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getSavedAt() {
        return savedAt;
    }
}
