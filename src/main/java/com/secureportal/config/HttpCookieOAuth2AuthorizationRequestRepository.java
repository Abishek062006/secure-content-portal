package com.secureportal.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.SerializationUtils;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.Base64;

/**
 * Stores the pending {@link OAuth2AuthorizationRequest} in a short-lived
 * cookie instead of the (default) HTTP session.
 *
 * <p>In production this app sits behind a Vercel rewrite proxy, and — even
 * with Spring Session JDBC backing sessions in Postgres, so no server
 * instance is stateful — real browsers were losing this specific
 * pre-authentication session between the initial
 * {@code /oauth2/authorization/google} redirect and Google's callback,
 * surfacing as {@code authorization_request_not_found}. The exact same round
 * trip replayed with curl and an explicit cookie jar worked correctly, which
 * ruled out the proxy and session-lookup logic themselves — something about
 * a real browser's handling of a session cookie across a redirect chain that
 * bounces through a third domain (accounts.google.com) and back was the
 * difference. A cookie needs no server-side session lookup at all for this
 * one narrow, short-lived value, removing that entire class of failure.
 */
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request";
    private static final Duration MAX_AGE = Duration.ofMinutes(3);

    private final boolean secureCookies;

    /**
     * {@code secureCookies} is passed in from the same
     * {@code server.servlet.session.cookie.secure} property that already
     * governs the session cookie — deliberately <em>not</em> detected
     * per-request via {@code request.isSecure()}, since that depends on
     * Vercel's proxy correctly forwarding {@code X-Forwarded-Proto} for
     * this specific hop, which is one more thing that could be silently
     * wrong. Reusing the existing, already-correct-in-both-profiles
     * property sidesteps that entirely.
     */
    public HttpCookieOAuth2AuthorizationRequestRepository(boolean secureCookies) {
        this.secureCookies = secureCookies;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, COOKIE_NAME);
        return cookie == null || cookie.getValue().isEmpty() ? null : deserialize(cookie.getValue());
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                          HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            clearCookie(response);
            return;
        }
        addCookie(response, serialize(authorizationRequest), MAX_AGE);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                   HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        clearCookie(response);
        return authorizationRequest;
    }

    private void clearCookie(HttpServletResponse response) {
        addCookie(response, "", Duration.ZERO);
    }

    private void addCookie(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, value)
                .path("/")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(secureCookies ? "None" : "Lax")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(authorizationRequest));
    }

    private OAuth2AuthorizationRequest deserialize(String cookieValue) {
        return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(Base64.getUrlDecoder().decode(cookieValue));
    }
}
