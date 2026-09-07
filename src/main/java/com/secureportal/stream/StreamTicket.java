package com.secureportal.stream;

import java.time.Instant;
import java.util.UUID;

/**
 * A short-lived, signed capability to fetch one content item's bytes for one
 * specific purpose, from one specific browser session. Unlike a plain
 * expiring URL, {@code sessionHash} means a ticket copied out of devtools is
 * dead the moment it's used anywhere but the session it was minted for —
 * not just eventually, immediately.
 */
public record StreamTicket(
        UUID contentId,
        Long userId,
        String sessionHash,
        Purpose purpose,
        Instant expiresAt,
        String nonce
) {

    public enum Purpose {
        VIDEO, PDF_PAGE, HTML
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
