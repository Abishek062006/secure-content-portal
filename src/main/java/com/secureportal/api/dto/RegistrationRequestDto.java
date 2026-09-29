package com.secureportal.api.dto;

import com.secureportal.course.CourseRegistrationRequest;

import java.time.Instant;
import java.util.UUID;

public record RegistrationRequestDto(
        Long id,
        UUID courseId,
        String courseTitle,
        Long userId,
        String userName,
        String userEmail,
        String status,
        String message,
        String decisionNote,
        Instant requestedAt,
        Instant decidedAt,
        String decidedBy
) {
    public static RegistrationRequestDto of(CourseRegistrationRequest r, String courseTitle, String userName, String userEmail) {
        return new RegistrationRequestDto(r.getId(), r.getCourseId(), courseTitle, r.getUserId(), userName, userEmail,
                r.getStatus().name(), r.getMessage(), r.getDecisionNote(), r.getRequestedAt(), r.getDecidedAt(), r.getDecidedBy());
    }
}
