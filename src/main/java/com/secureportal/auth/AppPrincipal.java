package com.secureportal.auth;

import com.secureportal.user.Role;
import com.secureportal.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The authenticated principal placed in the session.
 *
 * <p>It deliberately holds only immutable, serializable scalars rather than the
 * JPA {@link User} entity: the session is persisted to Postgres via Spring
 * Session, and a detached entity in there would be both unserializable and a
 * stale-data trap. The role carried here is the one loaded from our database at
 * login — never anything the OAuth provider asserted.
 */
public class AppPrincipal implements OidcUser, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String email;
    private final String displayName;
    private final String pictureUrl;
    private final Role role;
    private final Map<String, Object> claims;
    private final OidcIdToken idToken;
    private final OidcUserInfo userInfo;

    public AppPrincipal(User user, OidcUser delegate) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.pictureUrl = user.getPictureUrl();
        this.role = user.getRole();
        this.claims = Map.copyOf(delegate.getClaims());
        this.idToken = delegate.getIdToken();
        this.userInfo = delegate.getUserInfo();
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : email;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Initials shown in the header avatar when Google gives us no picture. */
    public String getInitials() {
        String source = getDisplayName().trim();
        if (source.isEmpty()) {
            return "?";
        }
        String[] parts = source.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public Map<String, Object> getClaims() {
        return claims;
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return idToken;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return claims;
    }

    @Override
    public String getName() {
        return email;
    }
}
