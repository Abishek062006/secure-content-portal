package com.secureportal.api;

import com.secureportal.api.dto.CertificateDto;
import com.secureportal.api.dto.CourseDto;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.certificate.Certificate;
import com.secureportal.certificate.CertificatePdfRenderer;
import com.secureportal.certificate.CertificateService;
import com.secureportal.course.Course;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.CourseStatus;
import com.secureportal.course.Enrollment;
import com.secureportal.course.EnrollmentRepository;
import com.secureportal.course.LearningService;
import com.secureportal.course.LessonProgressRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** "My learning" and completion certificates. */
@RestController
public class CertificateApiController {

    private final CertificateService certificateService;
    private final CertificatePdfRenderer renderer;
    private final LearningService learningService;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonProgressRepository progressRepository;
    private final CourseOutlineAssembler assembler;

    public CertificateApiController(CertificateService certificateService, CertificatePdfRenderer renderer,
                                    LearningService learningService, CourseRepository courseRepository,
                                    EnrollmentRepository enrollmentRepository, LessonProgressRepository progressRepository,
                                    CourseOutlineAssembler assembler) {
        this.certificateService = certificateService;
        this.renderer = renderer;
        this.learningService = learningService;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.progressRepository = progressRepository;
        this.assembler = assembler;
    }

    public record LearningItem(CourseDto course, UUID resumeLessonId, Instant enrolledAt, CertificateDto certificate) {
    }

    public record CertificateStatus(boolean earned, List<String> missing, CertificateDto certificate) {
    }

    public record Verification(boolean valid, String recipientName, String courseTitle, Instant issuedAt) {
    }

    /** Every course the learner is enrolled in, with progress, where to resume and any certificate. */
    @GetMapping("/api/me/learning")
    public List<LearningItem> myLearning(@AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            return List.of();
        }
        Long userId = principal.getUserId();
        Map<UUID, Enrollment> enrollments = new HashMap<>();
        enrollmentRepository.findByUserId(userId).forEach(e -> enrollments.put(e.getCourseId(), e));
        Map<UUID, Certificate> certificates = certificateService.forUser(userId).stream()
                .collect(Collectors.toMap(Certificate::getCourseId, c -> c));

        List<Course> courses = courseRepository.findByStatusOrderByCreatedAtDesc(CourseStatus.PUBLISHED).stream()
                .filter(c -> enrollments.containsKey(c.getId())).toList();
        List<CourseDto> dtos = assembler.catalog(courses, userId, progressRepository.countCompletedPerCourse(userId),
                enrollments.keySet());
        return dtos.stream().map(dto -> {
            Enrollment enrollment = enrollments.get(dto.id());
            Certificate certificate = certificates.get(dto.id());
            return new LearningItem(dto, assembler.resumeLessonFor(userId, dto.id(), enrollment),
                    enrollment.getEnrolledAt(), certificate == null ? null : CertificateDto.of(certificate));
        }).toList();
    }

    @GetMapping("/api/courses/{id}/certificate")
    public CertificateStatus status(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new com.secureportal.course.AdminNotALearnerException();
        }
        Course course = learningService.visibleCourse(id, principal.isAdmin());
        requireEnrolled(principal, course);
        return certificateService.find(principal.getUserId(), id)
                .map(c -> new CertificateStatus(true, List.of(), CertificateDto.of(c)))
                .orElseGet(() -> {
                    List<String> missing = certificateService.missing(principal.getUserId(), id);
                    return new CertificateStatus(missing.isEmpty(), missing, null);
                });
    }

    @PostMapping("/api/courses/{id}/certificate")
    public CertificateDto claim(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new com.secureportal.course.AdminNotALearnerException();
        }
        Course course = learningService.visibleCourse(id, principal.isAdmin());
        requireEnrolled(principal, course);
        return CertificateDto.of(certificateService.claim(principal.getUserId(), principal.getDisplayName(), course));
    }

    /** Only the holder can download the PDF; anyone with the code can verify it (below). */
    @GetMapping("/api/certificates/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        Certificate certificate = certificateService.findById(id)
                .filter(c -> c.getUserId().equals(principal.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"certificate-" + certificate.getCode() + ".pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(renderer.render(certificate));
    }

    @GetMapping("/api/certificates/verify/{code}")
    public Verification verify(@PathVariable String code) {
        return certificateService.findByCode(code)
                .map(c -> new Verification(true, c.getRecipientName(), c.getCourseTitle(), c.getIssuedAt()))
                .orElse(new Verification(false, null, null, null));
    }

    private void requireEnrolled(AppPrincipal principal, Course course) {
        if (learningService.enrollment(principal.getUserId(), course.getId()).isEmpty()) {
            throw new com.secureportal.course.EnrollmentRequiredException();
        }
    }
}
