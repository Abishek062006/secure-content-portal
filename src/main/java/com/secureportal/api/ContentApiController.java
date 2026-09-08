package com.secureportal.api;

import com.secureportal.api.dto.ContentDetailResponse;
import com.secureportal.api.dto.ContentItemDto;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.content.ContentItem;
import com.secureportal.content.ContentNotFoundException;
import com.secureportal.content.ContentRepository;
import com.secureportal.content.ContentService;
import com.secureportal.content.ContentType;
import com.secureportal.stream.StreamTicket;
import com.secureportal.stream.StreamTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** The viewer-facing side of the app: the library catalog and per-item view tickets. */
@RestController
@RequestMapping("/api/content")
public class ContentApiController {

    private static final int LIBRARY_PAGE_SIZE = 60;

    private final ContentRepository contentRepository;
    private final ContentService contentService;
    private final StreamTicketService ticketService;

    public ContentApiController(ContentRepository contentRepository, ContentService contentService,
                                 StreamTicketService ticketService) {
        this.contentRepository = contentRepository;
        this.contentService = contentService;
        this.ticketService = ticketService;
    }

    @GetMapping
    public List<ContentItemDto> list(@RequestParam(required = false) String search,
                                      @RequestParam(required = false) ContentType type,
                                      @RequestParam(required = false) String category) {
        Pageable pageable = PageRequest.of(0, LIBRARY_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        return contentRepository.search(blankToEmpty(search), type, blankToEmpty(category), pageable)
                .getContent().stream()
                .map(ContentItemDto::from)
                .toList();
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return contentRepository.findDistinctCategories();
    }

    /** Records a view and mints a fresh, session-bound ticket for this item's byte-serving endpoint. */
    @GetMapping("/{id}")
    public ContentDetailResponse detail(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal,
                                         HttpServletRequest request) {
        ContentItem item = contentRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException(id));

        contentService.recordView(id);

        StreamTicket.Purpose purpose = switch (item.getContentType()) {
            case VIDEO -> StreamTicket.Purpose.VIDEO;
            case PDF -> StreamTicket.Purpose.PDF_PAGE;
            case HTML -> StreamTicket.Purpose.HTML;
        };
        Duration ttl = item.getContentType() == ContentType.VIDEO ? Duration.ofMinutes(30) : Duration.ofMinutes(5);

        String ticket = ticketService.mint(id, principal.getUserId(), request, purpose, ttl);
        return new ContentDetailResponse(ContentItemDto.from(item), ticket);
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
