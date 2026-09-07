package com.secureportal.audit;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private static final int MAX_ENTRIES = 200;

    private final AuditRepository auditRepository;

    public AuditController(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @GetMapping("/admin/audit")
    public String audit(Model model) {
        model.addAttribute("entries",
                auditRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, MAX_ENTRIES)));
        return "admin/audit";
    }
}
