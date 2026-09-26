package com.secureportal.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An external hackathon an admin lists for learners. Learners register through the organiser's own link. */
@Entity
@Table(name = "hackathons")
public class Hackathon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 200)
    private String organizer;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Column(nullable = false, length = 100)
    private String stream;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(length = 200)
    private String location;

    @Column(name = "prize_pool", length = 100)
    private String prizePool;

    @Column(name = "registration_url", nullable = false, length = 1000)
    private String registrationUrl;

    @Column(name = "registration_deadline")
    private Instant registrationDeadline;

    @Column(name = "event_start_date")
    private Instant eventStartDate;

    @Column(name = "event_end_date")
    private Instant eventEndDate;

    @Column(nullable = false)
    private boolean featured;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "points_reward", nullable = false)
    private int pointsReward;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Hackathon() {
        // for JPA
    }

    /** Everything an admin can set, already validated by {@link HackathonService}. */
    public record Details(String title, String organizer, String description, String bannerUrl, String stream, HackathonMode mode,
                          String location, String prizePool, String registrationUrl, Instant registrationDeadline,
                          Instant eventStartDate, Instant eventEndDate, boolean featured, HackathonStatus status, int pointsReward) {
    }

    public Hackathon(Details details) {
        apply(details);
    }

    public void apply(Details details) {
        this.title = details.title();
        this.organizer = details.organizer();
        this.description = details.description();
        this.bannerUrl = details.bannerUrl();
        this.stream = details.stream();
        this.mode = details.mode().name();
        this.location = details.location();
        this.prizePool = details.prizePool();
        this.registrationUrl = details.registrationUrl();
        this.registrationDeadline = details.registrationDeadline();
        this.eventStartDate = details.eventStartDate();
        this.eventEndDate = details.eventEndDate();
        this.featured = details.featured();
        this.status = details.status().name();
        this.pointsReward = details.pointsReward();
        this.updatedAt = Instant.now();
    }

    /** Whether learners may still register: not finished, and the deadline (if any) hasn't passed. */
    public boolean acceptsRegistrations(Instant now) {
        return !HackathonStatus.COMPLETED.name().equals(status) && (registrationDeadline == null || !now.isAfter(registrationDeadline));
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getOrganizer() {
        return organizer;
    }

    public String getDescription() {
        return description;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public String getStream() {
        return stream;
    }

    public String getMode() {
        return mode;
    }

    public String getLocation() {
        return location;
    }

    public String getPrizePool() {
        return prizePool;
    }

    public String getRegistrationUrl() {
        return registrationUrl;
    }

    public Instant getRegistrationDeadline() {
        return registrationDeadline;
    }

    public Instant getEventStartDate() {
        return eventStartDate;
    }

    public Instant getEventEndDate() {
        return eventEndDate;
    }

    public boolean isFeatured() {
        return featured;
    }

    public String getStatus() {
        return status;
    }

    public int getPointsReward() {
        return pointsReward;
    }
}
