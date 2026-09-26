package com.secureportal.api.dto;

import com.secureportal.gamification.PointRule;

import java.time.Instant;

public record PointRuleDto(String id, String actionType, String displayName, int points, String description, Instant updatedAt) {

    public static PointRuleDto of(PointRule rule) {
        return new PointRuleDto(rule.getId(), rule.getActionType(), rule.getDisplayName(), rule.getPoints(), rule.getDescription(),
                rule.getUpdatedAt());
    }
}
