package com.secureportal.api;

import com.secureportal.analytics.PlatformAnalytics;
import com.secureportal.analytics.PlatformAnalyticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** URL-level access is enforced by SecurityConfig; {@code @PreAuthorize} is the independent method-level second check. */
@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsApiController {

    private final PlatformAnalyticsService analyticsService;

    public AdminAnalyticsApiController(PlatformAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public PlatformAnalytics analytics() {
        return analyticsService.compute();
    }
}
