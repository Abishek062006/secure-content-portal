package com.secureportal.testsupport;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.user.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Signs a request in as a real {@link AppPrincipal}, the way the OAuth login would. */
public final class TestPrincipals {

    private TestPrincipals() {
    }

    public static RequestPostProcessor as(User user) {
        OidcIdToken idToken = new OidcIdToken("test-id-token", Instant.now(), Instant.now().plusSeconds(3600),
                Map.of("sub", "test-subject", "iss", "https://accounts.google.com", "email", user.getEmail()));
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("sub", "test-subject", "email", user.getEmail()));
        DefaultOidcUser delegate = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, userInfo);
        AppPrincipal principal = new AppPrincipal(user, delegate);
        return authentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));
    }
}
