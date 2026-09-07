package com.secureportal.config;

import com.secureportal.auth.AppOidcUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Central access-control policy. {@code /admin/**} requires ADMIN here, at the
 * URL level, and is re-checked with {@code @PreAuthorize} on the service layer
 * once that exists (Phase 4) — a controller mistake alone should never be able
 * to expose an admin operation.
 *
 * <p>Role is never taken from the OAuth response; it is decided by
 * {@link AppOidcUserService} against our own {@code users} table.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AppOidcUserService appOidcUserService;

    public SecurityConfig(AppOidcUserService appOidcUserService) {
        this.appOidcUserService = appOidcUserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/error", "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(appOidcUserService))
                        .defaultSuccessUrl("/", false)
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
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
}
