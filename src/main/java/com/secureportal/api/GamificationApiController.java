package com.secureportal.api;

import com.secureportal.api.dto.CheckInDto;
import com.secureportal.api.dto.GamificationSummaryDto;
import com.secureportal.api.dto.PointTransactionDto;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.AdminNotALearnerException;
import com.secureportal.gamification.GamificationService;
import com.secureportal.gamification.GamificationService.BadgeStatus;
import com.secureportal.gamification.LeaderboardService;
import com.secureportal.gamification.LeaderboardService.LeaderboardResponse;
import com.secureportal.profile.ProfileNotFoundException;
import com.secureportal.user.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Points, streaks, badges and the leaderboard, for signed-in members. Only learners earn; admins just look. */
@RestController
public class GamificationApiController {

    private static final int HISTORY_LIMIT = 200;

    private final GamificationService gamificationService;
    private final LeaderboardService leaderboardService;
    private final UserRepository userRepository;

    public GamificationApiController(GamificationService gamificationService, LeaderboardService leaderboardService,
                                     UserRepository userRepository) {
        this.gamificationService = gamificationService;
        this.leaderboardService = leaderboardService;
        this.userRepository = userRepository;
    }

    @GetMapping("/api/gamification/me")
    public GamificationSummaryDto mySummary(@AuthenticationPrincipal AppPrincipal principal) {
        return GamificationSummaryDto.of(principal.getUserId(), gamificationService.summary(principal.getUserId()));
    }

    @PostMapping("/api/gamification/check-in")
    public CheckInDto checkIn(@AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
        return CheckInDto.of(gamificationService.checkIn(principal.getUserId()));
    }

    @GetMapping("/api/gamification/leaderboard")
    public LeaderboardResponse leaderboard(@RequestParam(defaultValue = "all_time") String timeframe,
                                           @RequestParam(required = false) String stream,
                                           @AuthenticationPrincipal AppPrincipal principal) {
        return leaderboardService.leaderboard(timeframe, stream, principal.getUserId());
    }

    @GetMapping("/api/gamification/badges")
    public List<BadgeStatus> myBadges(@AuthenticationPrincipal AppPrincipal principal) {
        return gamificationService.badgesOf(principal.getUserId());
    }

    /** Badges are part of a member's public profile, like their name and posts. */
    @GetMapping("/api/gamification/users/{userId}/badges")
    public List<BadgeStatus> badgesOf(@PathVariable Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ProfileNotFoundException();
        }
        return gamificationService.badgesOf(userId);
    }

    @GetMapping("/api/gamification/streams")
    public List<String> streams() {
        return leaderboardService.streams();
    }

    @GetMapping("/api/points/history")
    public List<PointTransactionDto> myHistory(@AuthenticationPrincipal AppPrincipal principal) {
        return gamificationService.historyOf(principal.getUserId(), HISTORY_LIMIT).stream().map(PointTransactionDto::of).toList();
    }
}
