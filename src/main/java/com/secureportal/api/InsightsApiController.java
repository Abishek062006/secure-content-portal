package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.insights.LearnerInsightsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A learner's own progress dashboard: completion, time invested, quiz/assessment trends, interview
 *  performance and their existing streak/points/badges, all in one read. */
@RestController
public class InsightsApiController {

    private final LearnerInsightsService insightsService;

    public InsightsApiController(LearnerInsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping("/api/me/insights")
    public LearnerInsightsService.Insights insights(@AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            return new LearnerInsightsService.Insights(0, 0, 0, java.util.List.of(), 0,
                    new LearnerInsightsService.QuizStats(0, 0, java.util.List.of(), java.util.List.of(), java.util.List.of(), null),
                    new LearnerInsightsService.InterviewStats(0, 0, null, null, null, null, null, null,
                            java.util.List.of(), null, null, java.util.List.of(), null),
                    null);
        }
        return insightsService.forUser(principal.getUserId());
    }
}
