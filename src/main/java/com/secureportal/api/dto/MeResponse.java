package com.secureportal.api.dto;

/** Shape of GET /api/me — the frontend's source of truth for auth state on load. */
public record MeResponse(boolean authenticated, UserDto user) {

    public static MeResponse anonymous() {
        return new MeResponse(false, null);
    }

    public static MeResponse of(UserDto user) {
        return new MeResponse(true, user);
    }
}
