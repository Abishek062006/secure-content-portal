package com.secureportal.config;

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

    public SpaAuthenticationHandlers(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        response.sendRedirect(appProperties.getFrontendUrl() + "/");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        String reason = URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8);
        response.sendRedirect(appProperties.getFrontendUrl() + "/login?error=" + reason);
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                 Authentication authentication) {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
