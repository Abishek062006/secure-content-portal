package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.gamification.GamificationService;
import com.secureportal.gamification.GamificationService.AdminLeaderboardResponse;
import com.secureportal.gamification.PointRule;
import com.secureportal.gamification.PointTransaction;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/gamification")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGamificationApiController {

    private final GamificationService gamificationService;

    public AdminGamificationApiController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @GetMapping("/leaderboard")
    public AdminLeaderboardResponse getLeaderboard(
            @RequestParam(defaultValue = "all_time") String timeframe,
            @RequestParam(required = false) String stream
    ) {
        return gamificationService.getAdminLeaderboard(timeframe, stream);
    }

    @GetMapping("/rules")
    public List<PointRule> getPointRules() {
        return gamificationService.getAllPointRules();
    }

    public record UpdateRuleRequest(int points) {}

    @PutMapping("/rules/{actionType}")
    public PointRule updatePointRule(
            @PathVariable String actionType,
            @RequestBody UpdateRuleRequest request,
            @AuthenticationPrincipal AppPrincipal principal
    ) {
        String adminEmail = principal != null ? principal.getEmail() : "admin@gradientnova.ai";
        return gamificationService.updatePointRule(actionType, request.points(), adminEmail);
    }

    public record AdjustXpRequest(Long userId, int amount, String reason) {}

    @PostMapping("/adjust")
    public void adjustXp(
            @RequestBody AdjustXpRequest request,
            @AuthenticationPrincipal AppPrincipal principal
    ) {
        String adminEmail = principal != null ? principal.getEmail() : "admin@gradientnova.ai";
        gamificationService.adjustPointsManual(request.userId(), request.amount(), request.reason(), adminEmail);
    }

    @GetMapping("/history")
    public List<PointTransaction> getPointHistory(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String stream
    ) {
        return gamificationService.getPointHistory(userId, actionType, stream);
    }
}
