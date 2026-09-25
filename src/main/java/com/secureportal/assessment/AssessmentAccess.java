package com.secureportal.assessment;

import com.secureportal.course.CourseModule;
import com.secureportal.course.CourseStructureService;
import com.secureportal.course.LearningService;
import com.secureportal.course.Lesson;
import com.secureportal.course.LessonProgress;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * When a learner may open a module or start an assessment:
 * <ul>
 *   <li>a module opens only after every earlier module's <em>gating</em> assessment is passed;</li>
 *   <li>a module's quiz/assessment opens once all of that module's lessons are completed;</li>
 *   <li>the final assessment opens once every lesson in the course is completed.</li>
 * </ul>
 */
@Component
public class AssessmentAccess {

    private final CourseStructureService structureService;
    private final AssessmentRepository assessmentRepository;
    private final AttemptRepository attemptRepository;
    private final LearningService learningService;

    public AssessmentAccess(CourseStructureService structureService, AssessmentRepository assessmentRepository,
                            AttemptRepository attemptRepository, LearningService learningService) {
        this.structureService = structureService;
        this.assessmentRepository = assessmentRepository;
        this.attemptRepository = attemptRepository;
        this.learningService = learningService;
    }

    /** Module id to the reason it can't be opened yet; modules that are open aren't in the map. */
    public Map<UUID, String> lockedModules(UUID courseId, Long userId) {
        Map<UUID, Assessment> byModule = new HashMap<>();
        assessmentRepository.findByCourseId(courseId).stream()
                .filter(a -> a.getModuleId() != null)
                .forEach(a -> byModule.put(a.getModuleId(), a));
        Set<UUID> passed = attemptRepository.passedAssessmentIds(userId);

        Map<UUID, String> locked = new HashMap<>();
        String reason = null;
        for (CourseModule module : structureService.modules(courseId)) {
            if (reason != null) {
                locked.put(module.getId(), reason);
                continue;
            }
            Assessment gate = byModule.get(module.getId());
            if (gate != null && gate.isGraded() && gate.isGatesNext() && !passed.contains(gate.getId())) {
                reason = "Pass \"" + gate.getTitle() + "\" to unlock this module.";
            }
        }
        return locked;
    }

    public Optional<String> lockReason(Assessment assessment, Long userId) {
        UUID courseId = assessment.getCourseId();
        return lockReason(assessment, lockedModules(courseId, userId), learningService.progress(userId, courseId),
                structureService.orderedLessons(courseId));
    }

    /** Same rules, reusing data the caller has already loaded (used when rendering a whole outline). */
    public Optional<String> lockReason(Assessment assessment, Map<UUID, String> lockedModules,
                                       Map<UUID, LessonProgress> progress, List<Lesson> lessons) {
        if (assessment.getModuleId() != null) {
            String moduleLock = lockedModules.get(assessment.getModuleId());
            if (moduleLock != null) {
                return Optional.of(moduleLock);
            }
            lessons = lessons.stream().filter(l -> l.getModuleId().equals(assessment.getModuleId())).collect(Collectors.toList());
            return incomplete(lessons, progress) ? Optional.of("Complete all lessons in this module first.") : Optional.empty();
        }
        return incomplete(lessons, progress) ? Optional.of("Complete every lesson in the course first.") : Optional.empty();
    }

    private static boolean incomplete(List<Lesson> lessons, Map<UUID, LessonProgress> progress) {
        return lessons.stream().anyMatch(l -> !(progress.containsKey(l.getId()) && progress.get(l.getId()).isCompleted()));
    }
}
