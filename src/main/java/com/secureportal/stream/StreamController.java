package com.secureportal.stream;

import com.secureportal.content.ContentItem;
import com.secureportal.content.ContentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Proxies video bytes from private storage. The browser never sees a storage
 * URL or credential — every request is authenticated by the caller's session
 * (SecurityConfig requires that just to reach this route) and additionally
 * verified against the ticket bound to that exact session.
 */
@RestController
public class StreamController {

    private final TicketGuard ticketGuard;
    private final ContentRepository contentRepository;
    private final RangedMediaResponder mediaResponder;

    public StreamController(TicketGuard ticketGuard, ContentRepository contentRepository,
                             RangedMediaResponder mediaResponder) {
        this.ticketGuard = ticketGuard;
        this.contentRepository = contentRepository;
        this.mediaResponder = mediaResponder;
    }

    @GetMapping("/api/stream/{ticket}")
    public ResponseEntity<InputStreamResource> stream(@PathVariable String ticket, HttpServletRequest request) {
        StreamTicket streamTicket = ticketGuard.verify(ticket, request, StreamTicket.Purpose.VIDEO);

        ContentItem item = contentRepository.findById(streamTicket.contentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        return mediaResponder.respond(item.getStorageKey(), item.getMimeType(), request);
    }
}
