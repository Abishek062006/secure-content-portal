package com.secureportal.audit;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public void log(String actorEmail, String action, UUID contentId, String detail) {
        auditRepository.save(new AuditLog(actorEmail, action, contentId, detail));
    }

    /** Same, but with where the request came from — used for login/logout, where that's the whole point. */
    public void log(String actorEmail, String action, UUID contentId, String detail, String ipAddress, String userAgent) {
        auditRepository.save(new AuditLog(actorEmail, action, contentId, detail, ipAddress, userAgent));
    }
}
