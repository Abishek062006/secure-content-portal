package com.secureportal.html;

import com.secureportal.content.ContentItem;
import com.secureportal.content.ContentRepository;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import com.secureportal.stream.StreamTicket;
import com.secureportal.stream.TicketGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * Serves already-sanitized HTML with a {@code sandbox} CSP that strips
 * scripts, forms, popups and same-origin treatment — the framed document
 * gets a null origin and cannot touch the parent page's session, even if
 * something slipped past {@link HtmlSanitizer} at upload time.
 */
@RestController
public class HtmlController {

    private final TicketGuard ticketGuard;
    private final ContentRepository contentRepository;
    private final StorageService storageService;

    public HtmlController(TicketGuard ticketGuard, ContentRepository contentRepository,
                           StorageService storageService) {
        this.ticketGuard = ticketGuard;
        this.contentRepository = contentRepository;
        this.storageService = storageService;
    }

    @GetMapping("/api/html/{ticket}")
    public ResponseEntity<byte[]> serve(@PathVariable String ticket, HttpServletRequest request) {
        StreamTicket streamTicket = ticketGuard.verify(ticket, request, StreamTicket.Purpose.HTML);

        ContentItem item = contentRepository.findById(streamTicket.contentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        byte[] content;
        try (StorageObject object = storageService.get(item.getStorageKey(), null, null)) {
            content = object.content().readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read HTML content " + item.getId(), e);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline");
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("Content-Security-Policy",
                "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data: https:;");

        return ResponseEntity.ok().headers(headers).contentType(MediaType.TEXT_HTML).body(content);
    }
}
