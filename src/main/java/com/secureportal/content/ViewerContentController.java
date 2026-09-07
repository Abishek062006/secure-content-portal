package com.secureportal.content;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.stream.StreamTicket;
import com.secureportal.stream.StreamTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

/**
 * A minimal, functional viewer page per content item — enough to mint a
 * ticket and exercise the protected delivery endpoints. The full library
 * grid with search/filter (and the /library route) is a separate phase;
 * this route and its templates carry forward unchanged into that.
 */
@Controller
public class ViewerContentController {

    private final ContentRepository contentRepository;
    private final StreamTicketService ticketService;

    public ViewerContentController(ContentRepository contentRepository, StreamTicketService ticketService) {
        this.contentRepository = contentRepository;
        this.ticketService = ticketService;
    }

    @GetMapping("/content/{id}")
    public String view(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal,
                        HttpServletRequest request, Model model) {
        ContentItem item = contentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        model.addAttribute("item", item);

        return switch (item.getContentType()) {
            case VIDEO -> {
                model.addAttribute("ticket", ticketService.mint(
                        id, principal.getUserId(), request, StreamTicket.Purpose.VIDEO, Duration.ofMinutes(30)));
                yield "view/video";
            }
            case PDF -> {
                model.addAttribute("ticket", ticketService.mint(
                        id, principal.getUserId(), request, StreamTicket.Purpose.PDF_PAGE, Duration.ofMinutes(5)));
                yield "view/pdf";
            }
            case HTML -> {
                model.addAttribute("ticket", ticketService.mint(
                        id, principal.getUserId(), request, StreamTicket.Purpose.HTML, Duration.ofMinutes(5)));
                yield "view/html";
            }
        };
    }
}
