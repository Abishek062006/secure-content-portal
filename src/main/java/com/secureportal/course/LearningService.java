package com.secureportal.course;

import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The learner's side: who can see a course, enrolling, and remembering where each learner is. */
@Service
public class LearningService {

    private static final Logger log = LoggerFactory.getLogger(LearningService.class);

    private final CourseService courseService;
    private final LessonRepository lessonRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonProgressRepository progressRepository;
    private final com.secureportal.gamification.GamificationService gamificationService;

    public LearningService(CourseService courseService, LessonRepository lessonRepository,
                           EnrollmentRepository enrollmentRepository, LessonProgressRepository progressRepository,
                           com.secureportal.gamification.GamificationService gamificationService) {
        this.courseService = courseService;
        this.lessonRepository = lessonRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.progressRepository = progressRepository;
        this.gamificationService = gamificationService;
    }

    /** Drafts don't exist as far as learners are concerned; admins may preview them. */
    public Course visibleCourse(UUID courseId, boolean admin) {
        Course course = courseService.find(courseId);
        if (course.getStatus() != CourseStatus.PUBLISHED && !admin) {
            throw new CourseNotFoundException(courseId);
        }
        return course;
    }

    public Lesson lessonOf(UUID courseId, UUID lessonId) {
        return lessonRepository.findById(lessonId)
                .filter(lesson -> lesson.getCourseId().equals(courseId))
                .orElseThrow(() -> new LessonNotFoundException(lessonId));
    }

    public Optional<Enrollment> enrollment(Long userId, UUID courseId) {
        return enrollmentRepository.findByUserIdAndCourseId(userId, courseId);
    }

    public Enrollment enroll(Long userId, Course course) {
        return enrollment(userId, course.getId()).orElseGet(() -> {
            try {
                return enrollmentRepository.save(new Enrollment(userId, course.getId()));
            } catch (DataIntegrityViolationException e) {
                return enrollment(userId, course.getId()).orElseThrow(() -> e);
            }
        });
    }

    public void visit(Enrollment enrollment, UUID lessonId) {
        enrollment.visit(lessonId);
        enrollmentRepository.save(enrollment);
    }

    public Map<UUID, LessonProgress> progress(Long userId, UUID courseId) {
        Map<UUID, LessonProgress> byLesson = new HashMap<>();
        progressRepository.findByUserIdAndCourseId(userId, courseId).forEach(p -> byLesson.put(p.getLessonId(), p));
        return byLesson;
    }

    public void saveProgress(Long userId, Lesson lesson, int positionSeconds, boolean completed) {
        LessonProgress progress = progressRepository.findByUserIdAndLessonId(userId, lesson.getId())
                .orElseGet(() -> new LessonProgress(userId, lesson.getId(), lesson.getCourseId()));
        boolean wasCompleted = progress.isCompleted();
        progress.record(positionSeconds, completed);
        try {
            progressRepository.save(progress);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request created the row first; apply this update to it instead.
            LessonProgress existing = progressRepository.findByUserIdAndLessonId(userId, lesson.getId())
                    .orElseThrow(() -> e);
            existing.record(positionSeconds, completed);
            progressRepository.save(existing);
        }

        if (completed && !wasCompleted) {
            rewardCompletion(userId, lesson);
        }
    }

    /** Points are a bonus on top of learning: whatever goes wrong in them must never lose the learner's progress. */
    private void rewardCompletion(Long userId, Lesson lesson) {
        try {
            String stream = courseService.find(lesson.getCourseId()).getCategory();
            gamificationService.recordLessonCompletion(userId, lesson.getId().toString(), stream);
            if (progressPercent(userId, lesson.getCourseId()) >= 100) {
                gamificationService.recordCourseCompletion(userId, lesson.getCourseId().toString(), stream);
            }
        } catch (RuntimeException e) {
            log.warn("Could not award points for lesson {} to user {}", lesson.getId(), userId, e);
        }
    }

    public int progressPercent(Long userId, UUID courseId) {
        return percent(progressRepository.countByUserIdAndCourseIdAndCompletedTrue(userId, courseId),
                lessonRepository.countByCourseId(courseId));
    }

    public static int percent(long completed, long total) {
        return total == 0 ? 0 : (int) Math.round(100.0 * completed / total);
    }
}
