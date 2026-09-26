package com.secureportal.hackathon;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "hackathons")
public class Hackathon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String organizer = "GradientNova & Global Partners";

    @Column(columnDefinition = "TEXT")
    private String description;

    private String bannerUrl;

    @Column(nullable = false)
    private String stream; // e.g. "Engineering & Web Dev", "AI & Data Science", "Global"

    @Column(nullable = false)
    private String mode; // "ONLINE", "OFFLINE", "HYBRID"

    private String location;

    private String prizePool;

    @Column(nullable = false, length = 1000)
    private String registrationUrl;

    private Instant registrationDeadline;
    private Instant eventStartDate;
    private Instant eventEndDate;

    private boolean featured;

    @Column(nullable = false)
    private String status; // "ACTIVE", "UPCOMING", "COMPLETED"

    private int pointsReward = 25;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public Hackathon() {}

    public Hackathon(String title, String organizer, String description, String bannerUrl, String stream, String mode, String location, String prizePool, String registrationUrl, Instant registrationDeadline, Instant eventStartDate, Instant eventEndDate, boolean featured, String status, int pointsReward) {
        this.title = title;
        this.organizer = (organizer != null && !organizer.isBlank()) ? organizer : "GradientNova & Global Partners";
        this.description = description;
        this.bannerUrl = bannerUrl;
        this.stream = stream;
        this.mode = mode;
        this.location = location;
        this.prizePool = prizePool;
        this.registrationUrl = registrationUrl;
        this.registrationDeadline = registrationDeadline;
        this.eventStartDate = eventStartDate;
        this.eventEndDate = eventEndDate;
        this.featured = featured;
        this.status = status;
        this.pointsReward = pointsReward;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOrganizer() { return organizer; }
    public void setOrganizer(String organizer) { this.organizer = organizer; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBannerUrl() { return bannerUrl; }
    public void setBannerUrl(String bannerUrl) { this.bannerUrl = bannerUrl; }

    public String getStream() { return stream; }
    public void setStream(String stream) { this.stream = stream; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getPrizePool() { return prizePool; }
    public void setPrizePool(String prizePool) { this.prizePool = prizePool; }

    public String getRegistrationUrl() { return registrationUrl; }
    public void setRegistrationUrl(String registrationUrl) { this.registrationUrl = registrationUrl; }

    public Instant getRegistrationDeadline() { return registrationDeadline; }
    public void setRegistrationDeadline(Instant registrationDeadline) { this.registrationDeadline = registrationDeadline; }

    public Instant getEventStartDate() { return eventStartDate; }
    public void setEventStartDate(Instant eventStartDate) { this.eventStartDate = eventStartDate; }

    public Instant getEventEndDate() { return eventEndDate; }
    public void setEventEndDate(Instant eventEndDate) { this.eventEndDate = eventEndDate; }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getPointsReward() { return pointsReward; }
    public void setPointsReward(int pointsReward) { this.pointsReward = pointsReward; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
