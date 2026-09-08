package com.secureportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * Emails granted ADMIN on login. This is the "seeded" role elevation the
     * brief allows in place of an admin-invite UI. Anyone not on this list is
     * a VIEWER.
     */
    private List<String> adminEmails = Collections.emptyList();

    /**
     * Secret used to sign stream tickets. Must be supplied in production; the
     * application refuses to start with the development default when the
     * {@code prod} profile is active.
     */
    private String ticketSecret = "dev-only-insecure-ticket-secret-change-me";

    /** How long a minted stream ticket stays valid. */
    private Duration ticketTtl = Duration.ofMinutes(5);

    /** DPI used when rasterising PDF pages server-side. */
    private int pdfRenderDpi = 110;

    /** Refuse PDFs longer than this, to bound rasterisation cost. */
    private int pdfMaxPages = 300;

    /**
     * Origin of the React SPA — used for CORS, and as the redirect target
     * after a successful (or failed) Google login, since the backend can no
     * longer redirect to its own "/" the way the Thymeleaf version did.
     */
    private String frontendUrl = "http://localhost:5173";

    public List<String> getAdminEmails() {
        return adminEmails;
    }

    public void setAdminEmails(List<String> adminEmails) {
        this.adminEmails = adminEmails;
    }

    public String getTicketSecret() {
        return ticketSecret;
    }

    public void setTicketSecret(String ticketSecret) {
        this.ticketSecret = ticketSecret;
    }

    public Duration getTicketTtl() {
        return ticketTtl;
    }

    public void setTicketTtl(Duration ticketTtl) {
        this.ticketTtl = ticketTtl;
    }

    public int getPdfRenderDpi() {
        return pdfRenderDpi;
    }

    public void setPdfRenderDpi(int pdfRenderDpi) {
        this.pdfRenderDpi = pdfRenderDpi;
    }

    public int getPdfMaxPages() {
        return pdfMaxPages;
    }

    public void setPdfMaxPages(int pdfMaxPages) {
        this.pdfMaxPages = pdfMaxPages;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public boolean isAdminEmail(String email) {
        if (email == null) {
            return false;
        }
        String normalised = email.trim().toLowerCase(Locale.ROOT);
        return adminEmails.stream()
                .map(candidate -> candidate.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalised::equals);
    }
}
