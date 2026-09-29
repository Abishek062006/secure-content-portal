package com.secureportal.course;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CourseEnquiryService {

    private static final int MAX_MESSAGE = 1000;

    private final CourseEnquiryRepository enquiries;
    private final CourseService courseService;

    public CourseEnquiryService(CourseEnquiryRepository enquiries, CourseService courseService) {
        this.enquiries = enquiries;
        this.courseService = courseService;
    }

    @Transactional
    public CourseEnquiry submit(UUID courseId, Long userId, String name, String email, String phone, String message) {
        courseService.find(courseId); // 404s if the course doesn't exist
        return enquiries.save(new CourseEnquiry(courseId, userId, name.trim(), email.trim(),
                blankToNull(phone), clip(message)));
    }

    @Transactional
    public CourseEnquiry markContacted(Long id, String adminEmail) {
        CourseEnquiry enquiry = enquiries.findById(id).orElseThrow(EnquiryNotFoundException::new);
        enquiry.markContacted(adminEmail);
        return enquiry;
    }

    @Transactional(readOnly = true)
    public List<CourseEnquiry> newOnes() {
        return enquiries.findByStatusOrderByCreatedAtDesc(EnquiryStatus.NEW);
    }

    @Transactional(readOnly = true)
    public List<CourseEnquiry> all() {
        return enquiries.findAllByOrderByCreatedAtDesc();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String clip(String value) {
        String clean = blankToNull(value);
        return clean != null && clean.length() > MAX_MESSAGE ? clean.substring(0, MAX_MESSAGE) : clean;
    }
}
