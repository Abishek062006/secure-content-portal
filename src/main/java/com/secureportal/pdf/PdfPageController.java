package com.secureportal.pdf;

import com.secureportal.content.ContentItem;
import com.secureportal.content.ContentRepository;
import com.secureportal.stream.StreamTicket;
import com.secureportal.stream.TicketGuard;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PdfPageController {

    private final TicketGuard ticketGuard;
    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final PdfRenderService pdfRenderService;

    public PdfPageController(TicketGuard ticketGuard, ContentRepository contentRepository,
                              UserRepository userRepository, PdfRenderService pdfRenderService) {
        this.ticketGuard = ticketGuard;
        this.contentRepository = contentRepository;
        this.userRepository = userRepository;
        this.pdfRenderService = pdfRenderService;
    }

    @GetMapping(value = "/api/pdf/{ticket}/page/{page}", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> page(@PathVariable String ticket, @PathVariable int page,
                                        HttpServletRequest request) {
        StreamTicket streamTicket = ticketGuard.verify(ticket, request, StreamTicket.Purpose.PDF_PAGE);

        ContentItem item = contentRepository.findById(streamTicket.contentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        User viewer = userRepository.findById(streamTicket.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        byte[] jpeg = pdfRenderService.renderPage(item, page, viewer.getEmail());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline");
        headers.add("X-Content-Type-Options", "nosniff");

        return ResponseEntity.ok().headers(headers).contentType(MediaType.IMAGE_JPEG).body(jpeg);
    }
}
