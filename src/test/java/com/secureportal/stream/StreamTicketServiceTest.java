package com.secureportal.stream;

import com.secureportal.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No Spring context needed — {@link StreamTicketService} only depends on
 * {@link AppProperties}, a plain POJO here, and a servlet request/session,
 * mocked via spring-test.
 */
class StreamTicketServiceTest {

    private final AppProperties appProperties = new AppProperties();
    private final StreamTicketService service;

    {
        appProperties.setTicketSecret("test-only-secret-do-not-use-in-prod");
        service = new StreamTicketService(appProperties);
    }

    private MockHttpServletRequest requestWithSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);
        return request;
    }

    @Test
    void validTicketVerifiesInTheSameSession() {
        MockHttpServletRequest request = requestWithSession();
        UUID contentId = UUID.randomUUID();
        String ticket = service.mint(contentId, 42L, request, StreamTicket.Purpose.VIDEO, Duration.ofMinutes(5));

        StreamTicket verified = service.verify(ticket, request);

        assertThat(verified.contentId()).isEqualTo(contentId);
        assertThat(verified.userId()).isEqualTo(42L);
        assertThat(verified.purpose()).isEqualTo(StreamTicket.Purpose.VIDEO);
    }

    @Test
    void ticketFailsInADifferentSession() {
        MockHttpServletRequest mintingRequest = requestWithSession();
        String ticket = service.mint(UUID.randomUUID(), 1L, mintingRequest,
                StreamTicket.Purpose.VIDEO, Duration.ofMinutes(5));

        MockHttpServletRequest otherSessionRequest = requestWithSession();

        assertThatThrownBy(() -> service.verify(ticket, otherSessionRequest))
                .isInstanceOf(TicketException.class)
                .hasMessageContaining("session");
    }

    @Test
    void ticketFailsWithNoSessionAtAll() {
        MockHttpServletRequest mintingRequest = requestWithSession();
        String ticket = service.mint(UUID.randomUUID(), 1L, mintingRequest,
                StreamTicket.Purpose.VIDEO, Duration.ofMinutes(5));

        MockHttpServletRequest noSessionRequest = new MockHttpServletRequest();

        assertThatThrownBy(() -> service.verify(ticket, noSessionRequest))
                .isInstanceOf(TicketException.class);
    }

    @Test
    void expiredTicketIsRejected() {
        MockHttpServletRequest request = requestWithSession();
        String ticket = service.mint(UUID.randomUUID(), 1L, request,
                StreamTicket.Purpose.VIDEO, Duration.ofMillis(-1));

        assertThatThrownBy(() -> service.verify(ticket, request))
                .isInstanceOf(TicketException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tamperedSignatureIsRejected() {
        MockHttpServletRequest request = requestWithSession();
        String ticket = service.mint(UUID.randomUUID(), 1L, request,
                StreamTicket.Purpose.VIDEO, Duration.ofMinutes(5));

        char last = ticket.charAt(ticket.length() - 1);
        String tampered = ticket.substring(0, ticket.length() - 1) + (last == 'a' ? 'b' : 'a');

        assertThatThrownBy(() -> service.verify(tampered, request))
                .isInstanceOf(TicketException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void payloadSwappedUnderTheOriginalSignatureIsRejected() {
        MockHttpServletRequest request = requestWithSession();
        String ticket = service.mint(UUID.randomUUID(), 1L, request,
                StreamTicket.Purpose.VIDEO, Duration.ofMinutes(5));

        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (UUID.randomUUID() + "|1|x|VIDEO|9999999999999|ab").getBytes());
        String forgedTicket = forgedPayload + ticket.substring(ticket.indexOf('.'));

        assertThatThrownBy(() -> service.verify(forgedTicket, request))
                .isInstanceOf(TicketException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void purposeRoundTripsCorrectly() {
        // StreamTicketService itself doesn't enforce purpose matching an
        // endpoint — TicketGuard does. This confirms purpose survives
        // minting/verification intact, which is what that check relies on.
        MockHttpServletRequest request = requestWithSession();
        String ticket = service.mint(UUID.randomUUID(), 1L, request,
                StreamTicket.Purpose.PDF_PAGE, Duration.ofMinutes(5));

        assertThat(service.verify(ticket, request).purpose()).isEqualTo(StreamTicket.Purpose.PDF_PAGE);
    }

    @Test
    void malformedTicketIsRejected() {
        MockHttpServletRequest request = requestWithSession();
        assertThatThrownBy(() -> service.verify("not-a-real-ticket", request))
                .isInstanceOf(TicketException.class);
    }
}
