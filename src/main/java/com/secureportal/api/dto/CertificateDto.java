package com.secureportal.api.dto;

import com.secureportal.certificate.Certificate;

import java.time.Instant;
import java.util.UUID;

public record CertificateDto(UUID id, String code, UUID courseId, String courseTitle, String recipientName,
                             Instant issuedAt) {
    public static CertificateDto of(Certificate c) {
        return new CertificateDto(c.getId(), c.getCode(), c.getCourseId(), c.getCourseTitle(), c.getRecipientName(),
                c.getIssuedAt());
    }
}
