package com.secureportal.stream;

import com.secureportal.content.ContentItem;
import com.secureportal.content.ContentRepository;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
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
    private final StorageService storageService;

    public StreamController(TicketGuard ticketGuard, ContentRepository contentRepository,
                             StorageService storageService) {
        this.ticketGuard = ticketGuard;
        this.contentRepository = contentRepository;
        this.storageService = storageService;
    }

    @GetMapping("/api/stream/{ticket}")
    public ResponseEntity<InputStreamResource> stream(@PathVariable String ticket, HttpServletRequest request) {
        StreamTicket streamTicket = ticketGuard.verify(ticket, request, StreamTicket.Purpose.VIDEO);

        ContentItem item = contentRepository.findById(streamTicket.contentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        RangeRequest range = RangeRequest.parse(request.getHeader(HttpHeaders.RANGE)).orElse(null);
        Long rangeStart = range != null ? range.start() : null;
        Long rangeEnd = range != null ? range.end() : null;

        StorageObject object = storageService.get(item.getStorageKey(), rangeStart, rangeEnd);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.add(HttpHeaders.CONTENT_TYPE, item.getMimeType());
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(object.rangeLength()));
        headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline");
        headers.add("X-Content-Type-Options", "nosniff");

        HttpStatus status = HttpStatus.OK;
        if (range != null) {
            headers.add(HttpHeaders.CONTENT_RANGE,
                    "bytes " + object.rangeStart() + "-" + object.rangeEnd() + "/" + object.totalSize());
            status = HttpStatus.PARTIAL_CONTENT;
        }

        return ResponseEntity.status(status).headers(headers).body(new InputStreamResource(object.content()));
    }
}
