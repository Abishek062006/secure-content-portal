package com.secureportal.api.dto;

import com.secureportal.gamification.Badge;

public record BadgeDto(String id, String title, String description, String category, String icon, int pointsReward, String rarity) {

    public static BadgeDto of(Badge badge) {
        return new BadgeDto(badge.getId(), badge.getTitle(), badge.getDescription(), badge.getCategory(), badge.getIcon(),
                badge.getPointsReward(), badge.getRarity());
    }
}
