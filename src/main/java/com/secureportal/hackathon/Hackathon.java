package com.secureportal.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An external hackathon an admin lists for learners. Learners register on the organiser's own site and can save the listing here. */
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

    @Column(name = "registration_url", length = 1000)
    private String registrationUrl;

    @Column(name = "registration_deadline")
    private Instant registrationDeadline;

    @Column(name = "event_start_date")
    private Instant eventStartDate;

    @Column(name = "event_end_date")
    private Instant eventEndDate;

    @Column(nullable = false, length = 10)
    private String kind = HackathonKind.EXTERNAL.name();

    @Column(columnDefinition = "TEXT")
    private String rules;

    @Column(length = 500)
    private String tracks;

    @Column(columnDefinition = "TEXT")
    private String prizes;

    @Column(name = "min_team_size", nullable = false)
    private int minTeamSize = 1;

    @Column(name = "max_team_size", nullable = false)
    private int maxTeamSize = 4;

    @Column(name = "banner_key", length = 300)
    private String bannerKey;

    @Column(name = "banner_mime", length = 60)
    private String bannerMime;

    @Column(name = "results_published_at")
    private Instant resultsPublishedAt;

    @Column(name = "reminded_24h", nullable = false)
    private boolean reminded24h = false;

    @Column(name = "reminded_1h", nullable = false)
    private boolean reminded1h = false;

    @Column(nullable = false)
    private boolean featured;

    @Column(nullable = false, length = 20)
    private String status;

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
                          Instant eventStartDate, Instant eventEndDate, boolean featured, HackathonStatus status, Hosted hosted) {
    }

    /** What only a hosted event has. Null for an external listing. */
    public record Hosted(String rules, String tracks, String prizes, int minTeamSize, int maxTeamSize) {
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
        Hosted hosted = details.hosted();
        this.kind = (hosted == null ? HackathonKind.EXTERNAL : HackathonKind.HOSTED).name();
        this.rules = hosted == null ? null : hosted.rules();
        this.tracks = hosted == null ? null : hosted.tracks();
        this.prizes = hosted == null ? null : hosted.prizes();
        this.minTeamSize = hosted == null ? 1 : hosted.minTeamSize();
        this.maxTeamSize = hosted == null ? 4 : hosted.maxTeamSize();
        this.updatedAt = Instant.now();
    }

    public boolean isHosted() {
        return HackathonKind.HOSTED.name().equals(kind);
    }

    /** The phase of a hosted event at {@code now}; null for an external listing. */
    public HackathonPhase phase(Instant now) {
        if (!isHosted()) {
            return null;
        }
        if (resultsPublishedAt != null) {
            return HackathonPhase.RESULTS;
        }
        if (now.isBefore(eventStartDate)) {
            return HackathonPhase.REGISTRATION;
        }
        return now.isBefore(eventEndDate) ? HackathonPhase.BUILDING : HackathonPhase.JUDGING;
    }

    /**
     * Whether people can still sign up. For an external listing that is the organiser's registration: not finished and the deadline
     * (if any) not passed. For a hosted event it is the registration phase, up to the deadline.
     */
    public boolean registrationOpen(Instant now) {
        if (isHosted()) {
            return phase(now) == HackathonPhase.REGISTRATION && !now.isAfter(registrationDeadline);
        }
        return !HackathonStatus.COMPLETED.name().equals(status) && (registrationDeadline == null || !now.isAfter(registrationDeadline));
    }

    public void setBanner(String key, String mime) {
        this.bannerKey = key;
        this.bannerMime = mime;
        this.updatedAt = Instant.now();
    }

    public String getBannerKey() {
        return bannerKey;
    }

    public String getBannerMime() {
        return bannerMime;
    }

    public void publishResults(Instant now) {
        this.resultsPublishedAt = now;
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

    public String getKind() {
        return kind;
    }

    public String getRules() {
        return rules;
    }

    public String getTracks() {
        return tracks;
    }

    public String getPrizes() {
        return prizes;
    }

    public int getMinTeamSize() {
        return minTeamSize;
    }

    public int getMaxTeamSize() {
        return maxTeamSize;
    }

    public Instant getResultsPublishedAt() {
        return resultsPublishedAt;
    }

    public boolean isFeatured() {
        return featured;
    }

    public String getStatus() {
        return status;
    }

    public boolean isReminded24h() {
        return reminded24h;
    }

    public void markReminded24h() {
        this.reminded24h = true;
    }

    public boolean isReminded1h() {
        return reminded1h;
    }

    public void markReminded1h() {
        this.reminded1h = true;
    }

}
