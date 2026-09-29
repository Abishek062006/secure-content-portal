package com.secureportal.api.dto;

import com.secureportal.course.CourseEnquiry;

import java.time.Instant;
import java.util.UUID;

public record EnquiryDto(
        Long id,
        UUID courseId,
        String courseTitle,
        String name,
        String email,
        String phone,
        String message,
        String status,
        Instant createdAt,
        Instant contactedAt,
        String contactedBy
) {
    public static EnquiryDto of(CourseEnquiry e, String courseTitle) {
        return new EnquiryDto(e.getId(), e.getCourseId(), courseTitle, e.getName(), e.getEmail(), e.getPhone(),
                e.getMessage(), e.getStatus().name(), e.getCreatedAt(), e.getContactedAt(), e.getContactedBy());
    }
}
