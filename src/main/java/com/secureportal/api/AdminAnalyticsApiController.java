package com.secureportal.api;

import com.secureportal.analytics.PlatformAnalytics;
import com.secureportal.analytics.PlatformAnalyticsService;
import com.secureportal.pdf.AdminPlatformReportPdfRenderer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final AdminPlatformReportPdfRenderer reportPdfRenderer;

    public AdminAnalyticsApiController(PlatformAnalyticsService analyticsService, AdminPlatformReportPdfRenderer reportPdfRenderer) {
        this.analyticsService = analyticsService;
        this.reportPdfRenderer = reportPdfRenderer;
    }

    @GetMapping
    public PlatformAnalytics analytics() {
        return analyticsService.compute();
    }

    @GetMapping("/report/pdf")
    public ResponseEntity<byte[]> downloadReport() {
        byte[] pdf = reportPdfRenderer.render(analyticsService.compute());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"gradientnova-platform-report.pdf\"")
                .body(pdf);
    }
}
