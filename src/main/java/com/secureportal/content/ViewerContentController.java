package com.secureportal.content;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.stream.StreamTicket;
import com.secureportal.stream.StreamTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

/** The viewer-facing side of the app: the library catalog and the per-item player pages. */
@Controller
public class ViewerContentController {

    private static final int LIBRARY_PAGE_SIZE = 60;

    private final ContentRepository contentRepository;
    private final ContentService contentService;
    private final StreamTicketService ticketService;

    public ViewerContentController(ContentRepository contentRepository, ContentService contentService,
                                    StreamTicketService ticketService) {
        this.contentRepository = contentRepository;
        this.contentService = contentService;
        this.ticketService = ticketService;
    }

    @GetMapping("/library")
    public String library(@RequestParam(required = false) String search,
                           @RequestParam(required = false) ContentType type,
                           @RequestParam(required = false) String category,
                           Model model) {
        Pageable pageable = PageRequest.of(0, LIBRARY_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        // The repository query needs "" rather than null to disable a filter
        // — see the Javadoc on ContentRepository.search for why.
        String searchQuery = blankToEmpty(search);
        String categoryQuery = blankToEmpty(category);

        model.addAttribute("items", contentRepository.search(searchQuery, type, categoryQuery, pageable).getContent());
        model.addAttribute("categories", contentRepository.findDistinctCategories());
        model.addAttribute("contentTypes", ContentType.values());
        model.addAttribute("search", blankToNull(search));
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedCategory", blankToNull(category));
        return "library";
    }

    @GetMapping("/content/{id}")
    public String view(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal,
                        HttpServletRequest request, Model model) {
        ContentItem item = contentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        contentService.recordView(id);
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

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
