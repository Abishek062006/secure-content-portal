package com.secureportal.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.SerializationUtils;

import java.util.Base64;

/**
 * Companion to {@link StatelessOAuth2AuthorizationRequestResolver}: the
 * pending request is encoded into {@code state} itself, so there is nothing
 * to actually store — this just decodes it back out of the incoming
 * request's own {@code state} parameter.
 */
public class StatelessOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return decode(request.getParameter(OAuth2ParameterNames.STATE));
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                          HttpServletRequest request, HttpServletResponse response) {
        // Nothing to do: StatelessOAuth2AuthorizationRequestResolver already
        // put everything into the state value itself before this could ever
        // be called, and there's nowhere else this needs to live.
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                   HttpServletResponse response) {
        return loadAuthorizationRequest(request);
    }

    private OAuth2AuthorizationRequest decode(String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        OAuth2AuthorizationRequest decoded;
        try {
            decoded = (OAuth2AuthorizationRequest) SerializationUtils.deserialize(Base64.getUrlDecoder().decode(state));
        } catch (RuntimeException e) {
            return null;
        }
        if (decoded == null) {
            return null;
        }
        // The deserialized object's own .state field is whatever Spring
        // originally generated before the resolver swapped it for this
        // encoded blob — rebuild with .state(state) so it matches exactly
        // what Google just echoed back, which is what Spring's own state
        // equality check (CSRF protection) compares against.
        return OAuth2AuthorizationRequest.from(decoded).state(state).build();
    }
}
