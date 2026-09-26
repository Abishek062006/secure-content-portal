package com.secureportal.api.dto;

import com.secureportal.gamification.GamificationService;

import java.util.List;

public record CheckInDto(int pointsEarned, int newStreak, boolean claimedToday, List<BadgeDto> unlockedBadges) {

    public static CheckInDto of(GamificationService.CheckInResult result) {
        return new CheckInDto(result.pointsEarned(), result.newStreak(), result.claimedToday(),
                result.unlockedBadges().stream().map(BadgeDto::of).toList());
    }
}
