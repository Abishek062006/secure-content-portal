package com.secureportal.course;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Asking for, and deciding on, access to a REGISTER-type course. Approval creates the same {@link Enrollment}
 *  an OPEN course's "Enroll" button would — from that point on a REGISTER course behaves exactly like any
 *  other enrolled course. */
@Service
public class CourseRegistrationService {

    private static final int MAX_MESSAGE = 1000;

    private final CourseRegistrationRequestRepository requests;
    private final CourseService courseService;
    private final LearningService learningService;

    public CourseRegistrationService(CourseRegistrationRequestRepository requests, CourseService courseService,
                                     LearningService learningService) {
        this.requests = requests;
        this.courseService = courseService;
        this.learningService = learningService;
    }

    @Transactional(readOnly = true)
    public Optional<CourseRegistrationRequest> myRequest(UUID courseId, Long userId) {
        return requests.findByCourseIdAndUserId(courseId, userId);
    }

    @Transactional
    public CourseRegistrationRequest request(UUID courseId, Long userId, String message) {
        Course course = courseService.find(courseId);
        if (course.getAccessType() != CourseAccessType.REGISTER) {
            throw new RegistrationRequestException("This course doesn't require a request — enroll directly instead.");
        }
        if (learningService.enrollment(userId, courseId).isPresent()) {
            throw new RegistrationRequestException("You already have access to this course.");
        }
        String clean = clip(message);
        return requests.findByCourseIdAndUserId(courseId, userId)
                .map(existing -> {
                    if (existing.getStatus() == RegistrationStatus.PENDING) {
                        throw new RegistrationRequestException("Your request is already waiting for review.");
                    }
                    if (existing.getStatus() == RegistrationStatus.APPROVED) {
                        throw new RegistrationRequestException("You already have access to this course.");
                    }
                    existing.resubmit(clean);
                    return existing;
                })
                .orElseGet(() -> requests.save(new CourseRegistrationRequest(courseId, userId, clean)));
    }

    @Transactional
    public CourseRegistrationRequest approve(Long requestId, String adminEmail, String note) {
        CourseRegistrationRequest req = find(requestId);
        req.approve(adminEmail, clip(note));
        Course course = courseService.find(req.getCourseId());
        learningService.enroll(req.getUserId(), course);
        return req;
    }

    @Transactional
    public CourseRegistrationRequest deny(Long requestId, String adminEmail, String note) {
        CourseRegistrationRequest req = find(requestId);
        req.deny(adminEmail, clip(note));
        return req;
    }

    @Transactional(readOnly = true)
    public List<CourseRegistrationRequest> forCourse(UUID courseId) {
        return requests.findByCourseIdOrderByRequestedAtDesc(courseId);
    }

    @Transactional(readOnly = true)
    public List<CourseRegistrationRequest> pending() {
        return requests.findByStatusOrderByRequestedAtDesc(RegistrationStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<CourseRegistrationRequest> all() {
        return requests.findAllByOrderByRequestedAtDesc();
    }

    private CourseRegistrationRequest find(Long id) {
        return requests.findById(id)
                .orElseThrow(() -> new RegistrationRequestException("That request no longer exists."));
    }

    private static String clip(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_MESSAGE ? trimmed.substring(0, MAX_MESSAGE) : trimmed;
    }
}
