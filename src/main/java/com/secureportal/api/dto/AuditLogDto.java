package com.secureportal.api.dto;

import com.secureportal.audit.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        Long id,
        String actorEmail,
        String action,
        UUID contentId,
        String detail,
        Instant createdAt
) {
    public static AuditLogDto from(AuditLog log) {
        return new AuditLogDto(log.getId(), log.getActorEmail(), log.getAction(),
                log.getContentId(), log.getDetail(), log.getCreatedAt());
    }
}
