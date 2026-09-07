package com.secureportal.user;

/**
 * Application roles. New accounts always start as {@link #VIEWER}; elevation to
 * {@link #ADMIN} is a seeded action driven by the {@code APP_ADMIN_EMAILS}
 * allow-list, never something a user can trigger themselves.
 */
public enum Role {
    VIEWER,
    ADMIN;

    /** Spring Security expects authorities to carry the {@code ROLE_} prefix. */
    public String authority() {
        return "ROLE_" + name();
    }
}
