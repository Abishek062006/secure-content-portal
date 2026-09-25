package com.secureportal.certificate;

import com.secureportal.assessment.Assessment;
import com.secureportal.assessment.AssessmentRepository;
import com.secureportal.assessment.AttemptRepository;
import com.secureportal.course.Course;
import com.secureportal.course.LearningService;
import com.secureportal.course.Lesson;
import com.secureportal.course.LessonProgress;
import com.secureportal.course.CourseStructureService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A certificate is earned by finishing every lesson and passing every graded assessment in the
 * course (module assessments and the final one). Quizzes are practice and never required.
 */
@Service
public class CertificateService {

    // No 0/O/1/I so a code read aloud or typed from a printout isn't ambiguous.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CertificateRepository certificateRepository;
    private final CourseStructureService structureService;
    private final LearningService learningService;
    private final AssessmentRepository assessmentRepository;
    private final AttemptRepository attemptRepository;

    public CertificateService(CertificateRepository certificateRepository, CourseStructureService structureService,
                              LearningService learningService, AssessmentRepository assessmentRepository,
                              AttemptRepository attemptRepository) {
        this.certificateRepository = certificateRepository;
        this.structureService = structureService;
        this.learningService = learningService;
        this.assessmentRepository = assessmentRepository;
        this.attemptRepository = attemptRepository;
    }

    /** What is still standing between this learner and the certificate; empty means it's earned. */
    public List<String> missing(Long userId, UUID courseId) {
        List<String> missing = new ArrayList<>();

        List<Lesson> lessons = structureService.orderedLessons(courseId);
        Map<UUID, LessonProgress> progress = learningService.progress(userId, courseId);
        long remaining = lessons.stream()
                .filter(l -> !(progress.containsKey(l.getId()) && progress.get(l.getId()).isCompleted()))
                .count();
        if (lessons.isEmpty()) {
            missing.add("This course has no lessons yet.");
        } else if (remaining > 0) {
            missing.add("Complete the remaining " + remaining + (remaining == 1 ? " lesson." : " lessons."));
        }

        Set<UUID> passed = attemptRepository.passedAssessmentIds(userId);
        for (Assessment assessment : assessmentRepository.findByCourseId(courseId)) {
            if (assessment.isGraded() && !passed.contains(assessment.getId())) {
                missing.add("Pass \"" + assessment.getTitle() + "\".");
            }
        }
        return missing;
    }

    public Optional<Certificate> find(Long userId, UUID courseId) {
        return certificateRepository.findByUserIdAndCourseId(userId, courseId);
    }

    public Optional<Certificate> findById(UUID id) {
        return certificateRepository.findById(id);
    }

    public Optional<Certificate> findByCode(String code) {
        return certificateRepository.findByCode(code == null ? "" : code.trim().toUpperCase());
    }

    public List<Certificate> forUser(Long userId) {
        return certificateRepository.findByUserId(userId);
    }

    /** Idempotent: a learner who already holds the certificate just gets it back. */
    public Certificate claim(Long userId, String recipientName, Course course) {
        Optional<Certificate> existing = find(userId, course.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        List<String> missing = missing(userId, course.getId());
        if (!missing.isEmpty()) {
            throw new CertificateNotEarnedException(missing);
        }
        String name = recipientName == null || recipientName.isBlank() ? "Learner" : recipientName.trim();
        for (int attempt = 0; ; attempt++) {
            try {
                return certificateRepository.saveAndFlush(
                        new Certificate(newCode(), userId, course.getId(), name, course.getTitle()));
            } catch (DataIntegrityViolationException e) {
                // Either a concurrent claim won (return theirs) or the code collided (try another).
                Optional<Certificate> winner = find(userId, course.getId());
                if (winner.isPresent()) {
                    return winner.get();
                }
                if (attempt >= 4) {
                    throw e;
                }
            }
        }
    }

    static String newCode() {
        StringBuilder code = new StringBuilder(14);
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
