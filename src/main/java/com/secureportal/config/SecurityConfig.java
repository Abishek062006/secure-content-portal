package com.secureportal.config;

import com.secureportal.auth.AppOidcUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Central access-control policy for the React-frontend architecture. The
 * frontend lives on a different origin from this API, so three things had to
 * change from the same-origin Thymeleaf version:
 *
 * <ul>
 *   <li>CORS is now explicit — {@link #corsConfigurationSource} allows only
 *   the configured frontend origin, with credentials, since cookies must
 *   cross that origin boundary on every request.</li>
 *   <li>CSRF tokens move to a cookie the frontend's JS can read
 *   ({@link CookieCsrfTokenRepository#withHttpOnlyFalse()}) and send back as
 *   a header — there's no server-rendered form to embed a hidden field in
 *   anymore. This matters <em>more</em> here, not less: cross-site cookies
 *   (see {@code application-prod.yml}'s {@code SameSite=None}) lose the
 *   built-in CSRF protection {@code SameSite=Lax} used to provide for free.</li>
 *   <li>Login/logout no longer redirect to a page this app renders —
 *   {@link SpaAuthenticationHandlers} sends the browser to the frontend's own
 *   URL instead.</li>
 *   <li>The pending OAuth2 authorization request is stored in a cookie
 *   ({@link HttpCookieOAuth2AuthorizationRequestRepository}) rather than the
 *   session — real browsers were observed losing that session specifically
 *   across the Vercel-proxied redirect chain through Google and back, even
 *   though Spring Session is Postgres-backed and the same round trip worked
 *   fine when replayed with curl and an explicit cookie jar.</li>
 * </ul>
 *
 * <p>Role is still never taken from the OAuth response; it is decided by
 * {@link AppOidcUserService} against our own {@code users} table, exactly as
 * before.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AppOidcUserService appOidcUserService;
    private final AppProperties appProperties;
    private final SpaAuthenticationHandlers spaAuthenticationHandlers;
    private final boolean secureCookies;

    public SecurityConfig(AppOidcUserService appOidcUserService, AppProperties appProperties,
                           SpaAuthenticationHandlers spaAuthenticationHandlers,
                           @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) {
        this.appOidcUserService = appOidcUserService;
        this.appProperties = appProperties;
        this.spaAuthenticationHandlers = spaAuthenticationHandlers;
        this.secureCookies = secureCookies;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        // withHttpOnlyFalse: the whole point is that the
                        // frontend's JS reads this cookie and echoes it back
                        // as a header — an HttpOnly cookie couldn't be read.
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Plain, not Xor: the raw cookie value must match
                        // exactly what's sent back in the X-XSRF-TOKEN
                        // header, with no masking the frontend would need to
                        // undo.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error", "/healthz", "/api/me").permitAll()
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(appOidcUserService))
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(new HttpCookieOAuth2AuthorizationRequestRepository(secureCookies)))
                        .successHandler(spaAuthenticationHandlers)
                        .failureHandler(spaAuthenticationHandlers)
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(spaAuthenticationHandlers)
                        .invalidateHttpSession(true)
                        .deleteCookies("SESSION")
                )
                // Default is DENY, which would also block our own viewer page
                // from framing the sandboxed HTML endpoint. SAMEORIGIN still
                // refuses any other site from framing us either way.
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin())
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                appProperties.getFrontendUrl(),
                "http://localhost:5173",
                "http://localhost:3000"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition", "Content-Range"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
