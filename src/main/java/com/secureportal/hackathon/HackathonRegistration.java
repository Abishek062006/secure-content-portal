package com.secureportal.hackathon;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "hackathon_registrations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"hackathonId", "userId"})
})
public class HackathonRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long hackathonId;

    @Column(nullable = false)
    private Long userId;

    private Instant registeredAt = Instant.now();

    private int pointsClaimed;

    public HackathonRegistration() {}

    public HackathonRegistration(Long hackathonId, Long userId, int pointsClaimed) {
        this.hackathonId = hackathonId;
        this.userId = userId;
        this.registeredAt = Instant.now();
        this.pointsClaimed = pointsClaimed;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getHackathonId() { return hackathonId; }
    public void setHackathonId(Long hackathonId) { this.hackathonId = hackathonId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }

    public int getPointsClaimed() { return pointsClaimed; }
    public void setPointsClaimed(int pointsClaimed) { this.pointsClaimed = pointsClaimed; }
}
