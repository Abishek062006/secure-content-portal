package com.secureportal.api;

import com.secureportal.api.dto.PointRuleDto;
import com.secureportal.api.dto.PointTransactionDto;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.gamification.GamificationService;
import com.secureportal.gamification.LeaderboardService;
import com.secureportal.gamification.LeaderboardService.AdminLeaderboardResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin controls over the points system: the leaderboard with contact details, the point rules, and audited manual adjustments. */
@RestController
@RequestMapping("/api/admin/gamification")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGamificationApiController {

    private static final int HISTORY_LIMIT = 200;

    public record UpdateRuleRequest(@Min(value = 0, message = "Points can't be negative.")
                                    @Max(value = 1000, message = "Points can't be more than 1000.") int points) {
    }

    public record AdjustXpRequest(@NotNull(message = "Choose a learner.") Long userId,
                                  @Min(value = -10000, message = "An adjustment can be at most 10000 XP.")
                                  @Max(value = 10000, message = "An adjustment can be at most 10000 XP.") int amount,
                                  @NotBlank(message = "Give a reason for the adjustment.")
                                  @Size(max = 200, message = "Keep the reason under 200 characters.") String reason) {
    }

    private final GamificationService gamificationService;
    private final LeaderboardService leaderboardService;

    public AdminGamificationApiController(GamificationService gamificationService, LeaderboardService leaderboardService) {
        this.gamificationService = gamificationService;
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("/leaderboard")
    public AdminLeaderboardResponse leaderboard(@RequestParam(defaultValue = "all_time") String timeframe,
                                                @RequestParam(required = false) String stream) {
        return leaderboardService.adminLeaderboard(timeframe, stream);
    }

    @GetMapping("/rules")
    public List<PointRuleDto> rules() {
        return gamificationService.pointRules().stream().map(PointRuleDto::of).toList();
    }

    @PutMapping("/rules/{actionType}")
    public PointRuleDto updateRule(@PathVariable String actionType, @Valid @RequestBody UpdateRuleRequest request,
                                   @AuthenticationPrincipal AppPrincipal admin) {
        return PointRuleDto.of(gamificationService.updatePointRule(actionType, request.points(), admin.getEmail()));
    }

    @PostMapping("/adjust")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void adjust(@Valid @RequestBody AdjustXpRequest request, @AuthenticationPrincipal AppPrincipal admin) {
        gamificationService.adjustPointsManual(request.userId(), request.amount(), request.reason(), admin.getEmail());
    }

    @GetMapping("/history")
    public List<PointTransactionDto> history(@RequestParam(required = false) Long userId,
                                             @RequestParam(required = false) String actionType,
                                             @RequestParam(required = false) String stream) {
        return gamificationService.history(userId, actionType, stream, HISTORY_LIMIT).stream().map(PointTransactionDto::of).toList();
    }
}
