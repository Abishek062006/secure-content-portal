package com.secureportal.api;

import com.secureportal.api.dto.AuditLogDto;
import com.secureportal.audit.AuditRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditApiController {

    private static final int MAX_ENTRIES = 200;

    private final AuditRepository auditRepository;

    public AdminAuditApiController(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping
    public List<AuditLogDto> list() {
        return auditRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, MAX_ENTRIES)).stream()
                .map(AuditLogDto::from)
                .toList();
    }
}
