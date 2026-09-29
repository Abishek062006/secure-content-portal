package com.secureportal.auth;

import com.secureportal.config.AppProperties;
import com.secureportal.notification.NotificationCategory;
import com.secureportal.notification.NotificationPriority;
import com.secureportal.notification.NotificationService;
import com.secureportal.user.Role;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Turns a freshly verified Google identity into an application principal.
 *
 * <p>This is where role assignment happens, and it is the only place it happens:
 * the role is read from (or seeded into) our own {@code users} table, so a
 * caller cannot influence it by tampering with the OAuth response.
 */
@Service
public class AppOidcUserService extends OidcUserService {

    private static final Logger log = LoggerFactory.getLogger(AppOidcUserService.class);

    private final UserRepository userRepository;
    private final AppProperties appProperties;
    private final NotificationService notificationService;

    public AppOidcUserService(UserRepository userRepository, AppProperties appProperties,
                              NotificationService notificationService) {
        this.userRepository = userRepository;
        this.appProperties = appProperties;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser delegate = super.loadUser(userRequest);

        String email = delegate.getEmail();
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_missing"),
                    "Google did not return an email address for this account.");
        }
        // Google sets this to false for accounts that have not completed
        // verification; treating those as authenticated would let someone claim
        // an address on the admin allow-list that is not theirs.
        if (Boolean.FALSE.equals(delegate.getEmailVerified())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("email_unverified"),
                    "This Google account does not have a verified email address.");
        }

        String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);
        Optional<User> existing = userRepository.findByEmailIgnoreCase(normalisedEmail);
        User user = existing.orElseGet(() -> {
            Role initialRole = appProperties.isAdminEmail(normalisedEmail) ? Role.ADMIN : Role.VIEWER;
            log.info("Provisioning new user {} with role {}", normalisedEmail, initialRole);
            return new User(normalisedEmail, delegate.getFullName(), delegate.getPicture(), initialRole);
        });

        user.setDisplayName(delegate.getFullName());
        user.setPictureUrl(delegate.getPicture());
        user.setLastLoginAt(Instant.now());

        // Keep the allow-list authoritative for promotion on every login, so an
        // admin can be seeded after the account already exists. Demotion is
        // deliberately left as a manual database action rather than something a
        // config edit can do silently.
        if (appProperties.isAdminEmail(normalisedEmail) && user.getRole() != Role.ADMIN) {
            log.info("Promoting {} to ADMIN via admin-email allow-list", normalisedEmail);
            user.setRole(Role.ADMIN);
        }

        User saved = userRepository.save(user);

        if (existing.isEmpty()) {
            notificationService.notifyAllAdmins(NotificationCategory.SYSTEM, "New user registered",
                    (saved.getDisplayName() != null ? saved.getDisplayName() : saved.getEmail()) + " joined as " + saved.getRole() + ".",
                    NotificationPriority.NORMAL, "/admin/users");
        }

        return new AppPrincipal(saved, delegate);
    }
}
