package com.secureportal.api;

import com.secureportal.api.dto.CourseDto;
import com.secureportal.api.dto.MaterialDto;
import com.secureportal.api.dto.CourseOutlineDto;
import com.secureportal.api.dto.CourseOutlineDto.LessonDto;
import com.secureportal.api.dto.CourseOutlineDto.ModuleDto;
import com.secureportal.api.dto.AssessmentSummaryDto;
import com.secureportal.assessment.Assessment;
import com.secureportal.assessment.AssessmentAccess;
import com.secureportal.assessment.AssessmentService;
import com.secureportal.assessment.Attempt;
import com.secureportal.assessment.AttemptRepository;
import com.secureportal.assessment.AttemptService;
import com.secureportal.course.Course;
import com.secureportal.course.CourseModuleRepository;
import com.secureportal.course.CourseStructureService;
import com.secureportal.course.Enrollment;
import com.secureportal.course.LearningService;
import com.secureportal.course.Lesson;
import com.secureportal.course.LessonProgress;
import com.secureportal.course.LessonRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Turns courses, modules, lessons and a learner's progress into the JSON the frontend renders. */
@Component
public class CourseOutlineAssembler {

    private final CourseStructureService structureService;
    private final LearningService learningService;
    private final CourseModuleRepository moduleRepository;
    private final LessonRepository lessonRepository;
    private final AssessmentService assessmentService;
    private final AssessmentAssembler assessmentAssembler;
    private final AssessmentAccess assessmentAccess;
    private final AttemptRepository attemptRepository;
    private final AttemptService attemptService;
    private final com.secureportal.course.MaterialService materialService;
    private final com.secureportal.course.EnrollmentRepository enrollmentRepository;
    private final com.secureportal.user.UserRepository userRepository;

    public CourseOutlineAssembler(CourseStructureService structureService, LearningService learningService,
                                  CourseModuleRepository moduleRepository, LessonRepository lessonRepository,
                                  AssessmentService assessmentService, AssessmentAssembler assessmentAssembler,
                                  AssessmentAccess assessmentAccess, AttemptRepository attemptRepository,
                                  AttemptService attemptService, com.secureportal.course.MaterialService materialService,
                                  com.secureportal.course.EnrollmentRepository enrollmentRepository,
                                  com.secureportal.user.UserRepository userRepository) {
        this.structureService = structureService;
        this.learningService = learningService;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
        this.assessmentService = assessmentService;
        this.assessmentAssembler = assessmentAssembler;
        this.assessmentAccess = assessmentAccess;
        this.attemptRepository = attemptRepository;
        this.attemptService = attemptService;
        this.materialService = materialService;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
    }

    /** The instructor is whoever created the course; looked up by id so no lazy proxy is touched. */
    private Map<Long, String> instructors(java.util.Collection<Course> courses) {
        Map<Long, String> names = new HashMap<>();
        userRepository.findAllById(courses.stream().map(c -> c.getUploadedBy().getId()).collect(Collectors.toSet()))
                .forEach(u -> names.put(u.getId(), u.getDisplayName()));
        return names;
    }

    private String instructor(Course course) {
        return instructors(List.of(course)).get(course.getUploadedBy().getId());
    }

    public CourseDto adminCourse(Course course) {
        return CourseDto.forAdmin(course, moduleRepository.countByCourseId(course.getId()),
                lessonRepository.countByCourseId(course.getId()), instructor(course));
    }

    public List<CourseDto> adminCourses(List<Course> courses) {
        Map<UUID, Long> modules = counts(moduleRepository.countModulesPerCourse());
        Map<UUID, Long> lessons = counts(lessonRepository.countLessonsPerCourse());
        Map<Long, String> names = instructors(courses);
        return courses.stream()
                .map(c -> CourseDto.forAdmin(c, modules.getOrDefault(c.getId(), 0L), lessons.getOrDefault(c.getId(), 0L),
                        names.get(c.getUploadedBy().getId())))
                .toList();
    }

    public List<CourseDto> catalog(List<Course> courses, Long userId, List<Object[]> completedPerCourse,
                                   Set<UUID> enrolledCourseIds) {
        Map<UUID, Long> modules = counts(moduleRepository.countModulesPerCourse());
        Map<UUID, Long> lessons = counts(lessonRepository.countLessonsPerCourse());
        Map<UUID, Long> completed = counts(completedPerCourse);
        Map<UUID, Long> enrollments = counts(enrollmentRepository.countEnrollmentsPerCourse());
        Map<Long, String> names = instructors(courses);
        return courses.stream().map(c -> CourseDto.forLearner(c,
                modules.getOrDefault(c.getId(), 0L), lessons.getOrDefault(c.getId(), 0L),
                enrolledCourseIds.contains(c.getId()),
                LearningService.percent(completed.getOrDefault(c.getId(), 0L), lessons.getOrDefault(c.getId(), 0L)),
                enrollments.getOrDefault(c.getId(), 0L), names.get(c.getUploadedBy().getId()))).toList();
    }

    public CourseOutlineDto adminOutline(Course course) {
        UUID courseId = course.getId();
        List<Assessment> assessments = assessmentService.forCourse(courseId);
        Assessment finalOne = assessments.stream().filter(a -> a.getModuleId() == null).findFirst().orElse(null);
        return new CourseOutlineDto(adminCourse(course), modules(courseId, null, true, assessments, Map.of(), null), false, 0,
                null, finalOne == null ? null : assessmentAssembler.admin(finalOne));
    }

    /** {@code admin} previews the course: nothing is locked for them. */
    public CourseOutlineDto learnerOutline(Course course, Long userId, boolean admin) {
        UUID courseId = course.getId();
        Map<UUID, LessonProgress> progress = learningService.progress(userId, courseId);
        Enrollment enrollment = learningService.enrollment(userId, courseId).orElse(null);
        List<Lesson> ordered = structureService.orderedLessons(courseId);

        long done = ordered.stream().filter(l -> progress.containsKey(l.getId()) && progress.get(l.getId()).isCompleted()).count();
        int percent = LearningService.percent(done, ordered.size());
        CourseDto dto = CourseDto.forLearner(course, moduleRepository.countByCourseId(courseId), ordered.size(),
                enrollment != null, percent, enrollmentRepository.countByCourseId(courseId), instructor(course));

        List<Assessment> assessments = assessmentService.forCourse(courseId);
        Map<UUID, Assessment> byId = assessments.stream().collect(Collectors.toMap(Assessment::getId, a -> a));
        attemptService.finalizeExpired(userId, byId);
        Map<UUID, String> locked = admin ? Map.of() : assessmentAccess.lockedModules(courseId, userId);
        Map<UUID, List<Attempt>> attempts = new HashMap<>();
        if (!byId.isEmpty()) {
            attemptRepository.findByUserIdAndAssessmentIdIn(userId, byId.keySet())
                    .forEach(x -> attempts.computeIfAbsent(x.getAssessmentId(), k -> new java.util.ArrayList<>()).add(x));
        }
        AssessmentContext context = new AssessmentContext(userId, admin, locked, attempts, progress, ordered);

        Assessment finalOne = assessments.stream().filter(a -> a.getModuleId() == null).findFirst().orElse(null);
        return new CourseOutlineDto(dto, modules(courseId, progress, false, assessments, locked, context), enrollment != null,
                percent, resumeLesson(enrollment, ordered, progress),
                finalOne == null ? null : learnerAssessment(finalOne, context));
    }

    /** What's needed to work out a learner's standing on each assessment without re-querying per assessment. */
    private record AssessmentContext(Long userId, boolean admin, Map<UUID, String> lockedModules,
                                     Map<UUID, List<Attempt>> attempts, Map<UUID, LessonProgress> progress,
                                     List<Lesson> lessons) {
    }

    private AssessmentSummaryDto learnerAssessment(Assessment a, AssessmentContext c) {
        var reason = c.admin() ? java.util.Optional.<String>empty()
                : assessmentAccess.lockReason(a, c.lockedModules(), c.progress(), c.lessons());
        return assessmentAssembler.learner(a, c.attempts().getOrDefault(a.getId(), List.of()), reason);
    }

    public LessonDto lessonDto(Lesson lesson, Map<UUID, LessonProgress> progress, boolean admin) {
        LessonProgress p = progress == null ? null : progress.get(lesson.getId());
        return new LessonDto(lesson.getId(), lesson.getTitle(), lesson.getDescription(), lesson.getPosition(),
                lesson.getTranscriptKey() != null, lesson.getVideoSizeLabel(),
                admin ? lesson.getVideoFilename() : null, admin ? lesson.getTranscriptFilename() : null,
                admin ? null : p != null && p.isCompleted(), admin ? null : p == null ? 0 : p.getPositionSeconds());
    }

    public ModuleDto moduleDto(com.secureportal.course.CourseModule module, List<Lesson> lessons,
                               Map<UUID, LessonProgress> progress, boolean admin) {
        return new ModuleDto(module.getId(), module.getTitle(), module.getDescription(), module.getPosition(),
                lessons.stream().sorted(Comparator.comparingInt(Lesson::getPosition))
                        .map(l -> lessonDto(l, progress, admin)).toList(), false, null, null, List.of());
    }

    private List<ModuleDto> modules(UUID courseId, Map<UUID, LessonProgress> progress, boolean admin,
                                    List<Assessment> assessments, Map<UUID, String> locked, AssessmentContext context) {
        Map<UUID, List<Lesson>> byModule = lessonRepository.findByCourseId(courseId).stream()
                .collect(Collectors.groupingBy(Lesson::getModuleId));
        Map<UUID, Assessment> assessmentByModule = new HashMap<>();
        assessments.stream().filter(a -> a.getModuleId() != null).forEach(a -> assessmentByModule.put(a.getModuleId(), a));

        Map<UUID, List<MaterialDto>> materialsByModule = materialService.forCourse(courseId).stream()
                .collect(Collectors.groupingBy(com.secureportal.course.CourseMaterial::getModuleId,
                        Collectors.mapping(MaterialDto::of, Collectors.toList())));
        return structureService.modules(courseId).stream().map(m -> {
            ModuleDto base = moduleDto(m, byModule.getOrDefault(m.getId(), List.of()), progress, admin);
            Assessment a = assessmentByModule.get(m.getId());
            AssessmentSummaryDto summary = a == null ? null
                    : admin ? assessmentAssembler.admin(a) : learnerAssessment(a, context);
            String reason = locked.get(m.getId());
            // A locked module's materials stay hidden from learners until it opens.
            List<MaterialDto> materials = reason != null && !admin ? List.of() : materialsByModule.getOrDefault(m.getId(), List.of());
            return new ModuleDto(base.id(), base.title(), base.description(), base.position(), base.lessons(),
                    reason != null, reason, summary, materials);
        }).toList();
    }

    public UUID resumeLessonFor(Long userId, UUID courseId, Enrollment enrollment) {
        return resumeLesson(enrollment, structureService.orderedLessons(courseId), learningService.progress(userId, courseId));
    }

    /** Where "Continue" goes: the last lesson opened, else the first unfinished one, else the first. */
    private UUID resumeLesson(Enrollment enrollment, List<Lesson> ordered, Map<UUID, LessonProgress> progress) {
        if (ordered.isEmpty()) {
            return null;
        }
        Set<UUID> ids = ordered.stream().map(Lesson::getId).collect(Collectors.toCollection(HashSet::new));
        if (enrollment != null && enrollment.getLastLessonId() != null && ids.contains(enrollment.getLastLessonId())) {
            return enrollment.getLastLessonId();
        }
        return ordered.stream()
                .filter(l -> !(progress.containsKey(l.getId()) && progress.get(l.getId()).isCompleted()))
                .findFirst().orElse(ordered.get(0)).getId();
    }

    private static Map<UUID, Long> counts(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }
}
