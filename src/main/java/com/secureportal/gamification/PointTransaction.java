package com.secureportal.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "point_transactions")
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int amount;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(length = 255)
    private String description;

    @Column(length = 100)
    private String stream;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_id", length = 100)
    private String sourceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PointTransaction() {
        // JPA
    }

    public PointTransaction(Long userId, int amount, String actionType, String description, String stream) {
        this(userId, amount, actionType, description, stream, null, null);
    }

    public PointTransaction(Long userId, int amount, String actionType, String description, String stream, String sourceType, String sourceId) {
        this.userId = userId;
        this.amount = amount;
        this.actionType = actionType;
        this.description = description;
        this.stream = stream;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public int getAmount() {
        return amount;
    }

    public String getActionType() {
        return actionType;
    }

    public String getDescription() {
        return description;
    }

    public String getStream() {
        return stream;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
