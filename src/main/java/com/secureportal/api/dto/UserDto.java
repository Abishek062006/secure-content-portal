package com.secureportal.api.dto;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.user.User;

import java.time.Instant;

public record UserDto(
        Long id,
        String email,
        String displayName,
        String pictureUrl,
        String role,
        boolean admin,
        Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPictureUrl(),
                user.getRole().name(),
                user.isAdmin(),
                user.getCreatedAt()
        );
    }

    /** For {@code GET /api/me} — built straight from the session principal, no DB round-trip. */
    public static UserDto from(AppPrincipal principal) {
        return new UserDto(
                principal.getUserId(),
                principal.getEmail(),
                principal.getDisplayName(),
                principal.getPictureUrl(),
                principal.getRole().name(),
                principal.isAdmin(),
                null
        );
    }
}
