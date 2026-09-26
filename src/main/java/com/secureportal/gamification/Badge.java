package com.secureportal.gamification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "badges")
public class Badge {

    @Id
    private String id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 50)
    private String icon;

    @Column(name = "points_reward", nullable = false)
    private int pointsReward = 100;

    @Column(nullable = false, length = 20)
    private String rarity = "COMMON";

    protected Badge() {
        // JPA
    }

    public Badge(String id, String title, String description, String category, String icon, int pointsReward, String rarity) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.icon = icon;
        this.pointsReward = pointsReward;
        this.rarity = rarity;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getIcon() {
        return icon;
    }

    public int getPointsReward() {
        return pointsReward;
    }

    public String getRarity() {
        return rarity;
    }
}
