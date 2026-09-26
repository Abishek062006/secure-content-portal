package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.gamification.GamificationService;
import com.secureportal.gamification.GamificationService.CheckInResult;
import com.secureportal.gamification.GamificationService.LeaderboardResponse;
import com.secureportal.gamification.GamificationService.UserBadgeDto;
import com.secureportal.gamification.UserGamification;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@RestController
public class GamificationApiController {

    private final GamificationService gamificationService;

    public GamificationApiController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    public record UserGamificationSummary(
            Long userId,
            int totalPoints,
            int currentStreak,
            int maxStreak,
            boolean checkedInToday,
            long unlockedBadgesCount
    ) {}

    @GetMapping("/api/gamification/me")
    public UserGamificationSummary getMySummary(@AuthenticationPrincipal AppPrincipal principal) {
        Long userId = principal.getUserId();
        UserGamification ug = gamificationService.getOrCreateGamification(userId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        boolean checkedInToday = Objects.equals(ug.getLastCheckinDate(), today);
        long badgesCount = gamificationService.getBadgesForUser(userId).stream().filter(UserBadgeDto::unlocked).count();

        return new UserGamificationSummary(
                userId,
                ug.getTotalPoints(),
                ug.getCurrentStreak(),
                ug.getMaxStreak(),
                checkedInToday,
                badgesCount
        );
    }

    @PostMapping("/api/gamification/check-in")
    public CheckInResult checkIn(@AuthenticationPrincipal AppPrincipal principal) {
        return gamificationService.checkIn(principal.getUserId());
    }

    @GetMapping("/api/gamification/leaderboard")
    public LeaderboardResponse getLeaderboard(
            @RequestParam(defaultValue = "all_time") String timeframe,
            @RequestParam(required = false) String stream,
            @AuthenticationPrincipal AppPrincipal principal
    ) {
        Long userId = principal != null ? principal.getUserId() : null;
        return gamificationService.getLeaderboard(timeframe, stream, userId);
    }

    @GetMapping("/api/gamification/badges")
    public List<UserBadgeDto> getBadges(@AuthenticationPrincipal AppPrincipal principal) {
        return gamificationService.getBadgesForUser(principal.getUserId());
    }

    @GetMapping("/api/gamification/users/{userId}/badges")
    public List<UserBadgeDto> getUserBadges(@PathVariable Long userId) {
        return gamificationService.getBadgesForUser(userId);
    }

    @GetMapping("/api/gamification/streams")
    public List<String> getStreams() {
        return gamificationService.getActiveStreams();
    }

    @GetMapping("/api/points/history")
    public List<com.secureportal.gamification.PointTransaction> getMyPointHistory(@AuthenticationPrincipal AppPrincipal principal) {
        return gamificationService.getPointHistory(principal.getUserId(), null, null);
    }
}
