package com.secureportal.config;

import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The Thymeleaf version could redirect straight to its own "/" after login.
 * A React SPA lives on a different origin entirely, so every one of these
 * handlers redirects there instead of relying on Spring Security's defaults.
 * Logout returns 204 rather than redirecting at all — the SPA calls it via
 * {@code fetch} and updates its own UI state from the response, there's no
 * page for the browser to navigate to.
 */
@Component
public class SpaAuthenticationHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler,
        LogoutSuccessHandler {

    private final AppProperties appProperties;
    private final AuditService auditService;

    public SpaAuthenticationHandlers(AppProperties appProperties, AuditService auditService) {
        this.appProperties = appProperties;
        this.auditService = auditService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        String email = authentication.getPrincipal() instanceof AppPrincipal principal
                ? principal.getEmail() : authentication.getName();
        auditService.log(email, "LOGIN", null, null, clientIp(request), userAgent(request));
        response.sendRedirect(appProperties.getFrontendUrl() + "/");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        auditService.log("unknown", "LOGIN_FAILED", null, exception.getMessage(), clientIp(request), userAgent(request));
        String reason = URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8);
        response.sendRedirect(appProperties.getFrontendUrl() + "/login?error=" + reason);
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                 Authentication authentication) {
        String email = authentication != null && authentication.getPrincipal() instanceof AppPrincipal principal
                ? principal.getEmail() : (authentication != null ? authentication.getName() : "unknown");
        auditService.log(email, "LOGOUT", null, null, clientIp(request), userAgent(request));
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    /** Behind a proxy (Render/Vercel), the real client address is the first hop in X-Forwarded-For, not
     *  {@code request.getRemoteAddr()}, which would just be the proxy itself. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua != null && ua.length() > 300 ? ua.substring(0, 300) : ua;
    }
}
