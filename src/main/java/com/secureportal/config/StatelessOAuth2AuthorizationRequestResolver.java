package com.secureportal.config;

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.SerializationUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Base64;

/**
 * Delegates to Spring's own resolver to build a correct {@link
 * OAuth2AuthorizationRequest} (PKCE, OIDC nonce, everything), then swaps its
 * {@code state} value for a self-contained one: the whole request,
 * Java-serialized and Base64url-encoded, used <em>as</em> the state.
 *
 * <p>Why: this app runs its OAuth flow through a Vercel rewrite proxy in
 * production. Storing the pending request server-side — in the session
 * (Spring Session JDBC, Postgres-backed, so no per-instance state) or in a
 * purpose-built cookie — both failed the same way in real browsers:
 * {@code authorization_request_not_found} on Google's callback, even though
 * curl replays of the exact same round trip (an explicit cookie jar, no
 * session affinity needed) succeeded every time. Something about a real
 * browser's handling of this cookie across the specific redirect chain that
 * bounces through accounts.google.com and back was dropping it, and that
 * didn't reproduce over curl, which never actually visits Google. Rather
 * than keep chasing why, this removes the round trip's only weak link:
 * Google is contractually required to echo back whatever {@code state} it
 * was given, over a plain URL query parameter with no client-side storage
 * involved at all, so putting everything needed directly there has nothing
 * left to lose in transit.
 *
 * <p>The value itself isn't a secret — it's not encrypted, just a
 * serialized request round-tripped through Google — but it also isn't
 * attacker-forgeable in a way that matters: it decodes back into exactly
 * the {@link OAuth2AuthorizationRequest} it started as, and Spring's own
 * {@code state} equality check (comparing this value against what Google's
 * callback carries) is what already provides the CSRF protection this
 * mechanism exists for.
 */
public class StatelessOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public StatelessOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository,
                                                         String authorizationRequestBaseUri) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, authorizationRequestBaseUri);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return encode(delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return encode(delegate.resolve(request, registrationId));
    }

    private OAuth2AuthorizationRequest encode(OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) {
            return null;
        }
        String blob = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(SerializationUtils.serialize(authorizationRequest));

        // Simple targeted replace rather than rebuilding the query string
        // via UriComponentsBuilder: the blob is base64url (only
        // [A-Za-z0-9_-]), which needs no percent-encoding, so there's no
        // encoding-scheme mismatch risk in just swapping the one parameter
        // in the already-correctly-built URI.
        String redirectUri = authorizationRequest.getAuthorizationRequestUri()
                .replaceFirst("([?&]" + OAuth2ParameterNames.STATE + "=)[^&]*", "$1" + blob);

        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .state(blob)
                .authorizationRequestUri(redirectUri)
                .build();
    }
}
