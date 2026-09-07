package com.secureportal.content;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.stream.StreamTicket;
import com.secureportal.stream.StreamTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Sort;
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
 * The viewer-facing side of the app: the library catalog and the per-item
 * player pages. Search/filter on top of {@code /library} is a separate
 * phase — {@link ContentRepository#search} already supports it, this route
 * just isn't wired to a search box yet.
 */
@Controller
public class ViewerContentController {

    private final ContentRepository contentRepository;
    private final StreamTicketService ticketService;

    public ViewerContentController(ContentRepository contentRepository, StreamTicketService ticketService) {
        this.contentRepository = contentRepository;
        this.ticketService = ticketService;
    }

    @GetMapping("/library")
    public String library(Model model) {
        model.addAttribute("items", contentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
        return "library";
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
