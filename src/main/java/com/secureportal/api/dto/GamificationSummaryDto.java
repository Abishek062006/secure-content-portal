package com.secureportal.api.dto;

import com.secureportal.gamification.GamificationService;

/** A learner's own points, streak and badge count. */
public record GamificationSummaryDto(Long userId, int totalPoints, int currentStreak, int maxStreak, boolean checkedInToday,
                                     long unlockedBadgesCount) {

    public static GamificationSummaryDto of(Long userId, GamificationService.Summary summary) {
        return new GamificationSummaryDto(userId, summary.totalPoints(), summary.currentStreak(), summary.maxStreak(),
                summary.checkedInToday(), summary.unlockedBadges());
    }
}
