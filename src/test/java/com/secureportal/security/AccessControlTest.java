package com.secureportal.security;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the access-control policy through the real Spring Security filter
 * chain and real controllers — not by reading SecurityConfig and trusting
 * it. The full application context boots against the real Neon database
 * (same as running the app), so this needs {@code .env} sourced — see the
 * README. Deliberately not run during the Docker build for that reason;
 * it's a dev-time/CI check, not a deploy-time one.
 *
 * <p>Authentication is simulated with a real {@link AppPrincipal} — not
 * spring-security-test's generic {@code oidcLogin()}, which builds a plain
 * {@code DefaultOidcUser} that templates using AppPrincipal-specific
 * properties (the nav bar's role badge, in particular) can't render. Using
 * the real principal type here means these tests also catch template bugs
 * that only a genuinely-typed principal would surface, which is exactly
 * what happened while writing this: {@code oidcLogin()} instead of this
 * tripped over {@code #authentication.principal.admin} not existing on
 * {@code DefaultOidcUser}.
 *
 * <p>None of these tests mutate real data: the two admin-authenticated
 * checks are read-only GETs, and the one destructive-route test only
 * exercises the anonymous/viewer rejection paths against a random,
 * non-existent id — it never runs as an authenticated admin.
 *
 * <p>The frontend is a separate React SPA now (see {@code frontend/}), so
 * routes like {@code /} and {@code /login} no longer exist on this backend
 * at all — this only exercises the {@code /api/**} surface it actually
 * serves.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessControlTest {

    private static final String[] ADMIN_GET_ROUTES = {
            "/api/admin/content",
            "/api/admin/audit",
            "/api/admin/users"
    };

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicApiEndpointsAreReachableByAnyone() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isOk());
        mockMvc.perform(get("/healthz")).andExpect(status().isOk());
    }

    @Test
    void anonymousUserIsRedirectedFromAdminRoutes() throws Exception {
        for (String route : ADMIN_GET_ROUTES) {
            mockMvc.perform(get(route)).andExpect(status().is3xxRedirection());
        }
    }

    @Test
    void viewerIsForbiddenFromAdminRoutes() throws Exception {
        for (String route : ADMIN_GET_ROUTES) {
            mockMvc.perform(get(route).with(asViewer())).andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCanReachAdminRoutes() throws Exception {
        for (String route : ADMIN_GET_ROUTES) {
            mockMvc.perform(get(route).with(asAdmin())).andExpect(status().isOk());
        }
    }

    @Test
    void adminDeleteRouteRejectsAnonymousAndViewerAlike() throws Exception {
        String randomId = UUID.randomUUID().toString();

        mockMvc.perform(delete("/api/admin/content/" + randomId).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(delete("/api/admin/content/" + randomId).with(csrf()).with(asViewer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void userPromoteAndDemoteRoutesRejectAnonymousAndViewerAlike() throws Exception {
        String randomId = "999999999";

        mockMvc.perform(post("/api/admin/users/" + randomId + "/promote").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/api/admin/users/" + randomId + "/promote").with(csrf()).with(asViewer()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/users/" + randomId + "/demote").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/api/admin/users/" + randomId + "/demote").with(csrf()).with(asViewer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserIsRedirectedFromTheLibraryAndContentEndpoints() throws Exception {
        mockMvc.perform(get("/api/content")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/content/" + UUID.randomUUID())).andExpect(status().is3xxRedirection());
    }

    @Test
    void viewerCanReachTheLibrary() throws Exception {
        mockMvc.perform(get("/api/content").with(asViewer())).andExpect(status().isOk());
    }

    @Test
    void deliveryEndpointsRejectAnonymousRequestsBeforeTouchingTicketLogic() throws Exception {
        mockMvc.perform(get("/api/stream/anything")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/pdf/anything/page/1")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/api/html/anything")).andExpect(status().is3xxRedirection());
    }

    private RequestPostProcessor asViewer() {
        return asPrincipal("access-control-test-viewer@example.com", Role.VIEWER);
    }

    private RequestPostProcessor asAdmin() {
        return asPrincipal("access-control-test-admin@example.com", Role.ADMIN);
    }

    private RequestPostProcessor asPrincipal(String email, Role role) {
        User user = new User(email, "Access Control Test", null, role);

        OidcIdToken idToken = new OidcIdToken(
                "test-id-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("sub", "test-subject", "iss", "https://accounts.google.com", "email", email));
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("sub", "test-subject", "email", email));
        DefaultOidcUser delegate = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, userInfo);

        AppPrincipal principal = new AppPrincipal(user, delegate);
        OAuth2AuthenticationToken token =
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        return authentication(token);
    }
}
