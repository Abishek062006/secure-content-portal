package com.secureportal.stream;

import com.secureportal.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Mints and verifies {@link StreamTicket}s: HMAC-SHA256 over a compact
 * pipe-delimited payload, Base64URL-encoded. Signature comparison uses
 * {@link MessageDigest#isEqual} rather than {@code String.equals} or
 * {@code Arrays.equals}, which both short-circuit on the first mismatched
 * byte and so leak timing information about how much of a forged signature
 * was correct.
 */
@Service
public class StreamTicketService {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final String DELIM = "|";

    private final SecureRandom secureRandom = new SecureRandom();
    private final AppProperties appProperties;

    public StreamTicketService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String mint(UUID contentId, Long userId, HttpServletRequest request,
                        StreamTicket.Purpose purpose, Duration ttl) {
        String sessionHash = sessionHash(request, true);
        String nonce = randomHex(8);
        long expiresAtMillis = Instant.now().plus(ttl).toEpochMilli();

        String payload = String.join(DELIM,
                contentId.toString(),
                String.valueOf(userId),
                sessionHash,
                purpose.name(),
                String.valueOf(expiresAtMillis),
                nonce);

        return base64UrlEncode(payload) + "." + sign(payload);
    }

    public StreamTicket verify(String ticket, HttpServletRequest request) {
        int dot = ticket.indexOf('.');
        if (dot < 0) {
            throw new TicketException("Malformed ticket");
        }

        String encodedPayload = ticket.substring(0, dot);
        String providedSignature = ticket.substring(dot + 1);

        String payload;
        try {
            payload = base64UrlDecode(encodedPayload);
        } catch (IllegalArgumentException e) {
            throw new TicketException("Malformed ticket");
        }

        if (!constantTimeEquals(sign(payload), providedSignature)) {
            throw new TicketException("Invalid ticket signature");
        }

        String[] parts = payload.split("\\" + DELIM, -1);
        if (parts.length != 6) {
            throw new TicketException("Malformed ticket");
        }

        StreamTicket streamTicket;
        try {
            streamTicket = new StreamTicket(
                    UUID.fromString(parts[0]),
                    Long.valueOf(parts[1]),
                    parts[2],
                    StreamTicket.Purpose.valueOf(parts[3]),
                    Instant.ofEpochMilli(Long.parseLong(parts[4])),
                    parts[5]);
        } catch (RuntimeException e) {
            throw new TicketException("Malformed ticket");
        }

        if (streamTicket.isExpired()) {
            throw new TicketException("Ticket expired");
        }

        String currentSessionHash = sessionHash(request, false);
        if (!constantTimeEquals(streamTicket.sessionHash(), currentSessionHash)) {
            throw new TicketException("Ticket is not valid for this session");
        }

        return streamTicket;
    }

    /**
     * Binds a ticket to the caller's session without ever putting the raw
     * session ID in the ticket itself. Verification with no session at all
     * (e.g. a private window with no cookies) fails immediately here, before
     * any signature or expiry check runs.
     */
    private String sessionHash(HttpServletRequest request, boolean createIfMissing) {
        HttpSession session = request.getSession(createIfMissing);
        if (session == null) {
            throw new TicketException("No active session");
        }
        return sign("session:" + session.getId()).substring(0, 24);
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(appProperties.getTicketSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign ticket", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlEncode(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private String base64UrlDecode(String s) {
        return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private String randomHex(int numBytes) {
        byte[] buf = new byte[numBytes];
        secureRandom.nextBytes(buf);
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
