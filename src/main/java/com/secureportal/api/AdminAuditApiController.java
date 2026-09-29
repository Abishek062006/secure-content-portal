package com.secureportal.api;

import com.secureportal.api.dto.AuditLogDto;
import com.secureportal.audit.AuditRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditApiController {

    private static final int PAGE_SIZE = 100;

    private final AuditRepository auditRepository;

    public AdminAuditApiController(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public record AuditPageDto(List<AuditLogDto> entries, int page, boolean hasMore, List<String> actions) {
    }

    /** Every parameter is optional; leaving all of them out is the old unfiltered "most recent entries" view. */
    @GetMapping
    public AuditPageDto list(@RequestParam(required = false) String action,
                             @RequestParam(required = false) String actor,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                             @RequestParam(defaultValue = "0") int page) {
        String actionFilter = blankToNull(action);
        String actorFilter = blankToNull(actor);
        Page<com.secureportal.audit.AuditLog> result = auditRepository.search(
                actionFilter, actorFilter, from, to, PageRequest.of(Math.max(0, page), PAGE_SIZE));
        return new AuditPageDto(result.getContent().stream().map(AuditLogDto::from).toList(),
                page, result.hasNext(), auditRepository.distinctActions());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
