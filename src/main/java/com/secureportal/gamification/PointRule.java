package com.secureportal.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "point_rules")
public class PointRule {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "action_type", nullable = false, unique = true, length = 50)
    private String actionType;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    private int points;

    @Column(length = 255)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PointRule() {
        // JPA
    }

    public PointRule(String id, String actionType, String displayName, int points, String description) {
        this.id = id;
        this.actionType = actionType;
        this.displayName = displayName;
        this.points = points;
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getActionType() {
        return actionType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
        this.updatedAt = Instant.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
