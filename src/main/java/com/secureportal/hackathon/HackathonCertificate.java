package com.secureportal.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** What a team member earned in a hosted hackathon: a winner, runner-up or participation certificate with its own verifiable code. */
@Entity
@Table(name = "hackathon_certificates")
public class HackathonCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "hackathon_id", nullable = false)
    private Long hackathonId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;

    @Column(name = "hackathon_title", nullable = false, length = 200)
    private String hackathonTitle;

    @Column(name = "team_name", length = 150)
    private String teamName;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type", nullable = false, length = 30)
    private HackathonCertificateType type;

    @Column(name = "ranking")
    private Integer rank;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    protected HackathonCertificate() {
        // for JPA
    }

    public HackathonCertificate(Long hackathonId, Long userId, String code, String hackathonTitle, String recipientName,
                                String teamName, HackathonCertificateType type, Integer rank) {
        this.code = code;
        this.hackathonId = hackathonId;
        this.userId = userId;
        this.recipientName = recipientName;
        this.hackathonTitle = hackathonTitle;
        this.teamName = teamName;
        this.type = type;
        this.rank = rank;
        this.issuedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Long getHackathonId() {
        return hackathonId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getHackathonTitle() {
        return hackathonTitle;
    }

    public String getTeamName() {
        return teamName;
    }

    public HackathonCertificateType getType() {
        return type;
    }

    public Integer getRank() {
        return rank;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
