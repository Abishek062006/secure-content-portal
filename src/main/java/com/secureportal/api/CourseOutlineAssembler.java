package com.secureportal.api;

import com.secureportal.api.dto.CourseDto;
import com.secureportal.api.dto.CourseOutlineDto;
import com.secureportal.api.dto.CourseOutlineDto.LessonDto;
import com.secureportal.api.dto.CourseOutlineDto.ModuleDto;
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

    public CourseOutlineAssembler(CourseStructureService structureService, LearningService learningService,
                                  CourseModuleRepository moduleRepository, LessonRepository lessonRepository) {
        this.structureService = structureService;
        this.learningService = learningService;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
    }

    public CourseDto adminCourse(Course course) {
        return CourseDto.forAdmin(course, moduleRepository.countByCourseId(course.getId()),
                lessonRepository.countByCourseId(course.getId()));
    }

    public List<CourseDto> adminCourses(List<Course> courses) {
        Map<UUID, Long> modules = counts(moduleRepository.countModulesPerCourse());
        Map<UUID, Long> lessons = counts(lessonRepository.countLessonsPerCourse());
        return courses.stream()
                .map(c -> CourseDto.forAdmin(c, modules.getOrDefault(c.getId(), 0L), lessons.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    public List<CourseDto> catalog(List<Course> courses, Long userId, List<Object[]> completedPerCourse,
                                   Set<UUID> enrolledCourseIds) {
        Map<UUID, Long> modules = counts(moduleRepository.countModulesPerCourse());
        Map<UUID, Long> lessons = counts(lessonRepository.countLessonsPerCourse());
        Map<UUID, Long> completed = counts(completedPerCourse);
        return courses.stream().map(c -> CourseDto.forLearner(c,
                modules.getOrDefault(c.getId(), 0L), lessons.getOrDefault(c.getId(), 0L),
                enrolledCourseIds.contains(c.getId()),
                LearningService.percent(completed.getOrDefault(c.getId(), 0L), lessons.getOrDefault(c.getId(), 0L)))).toList();
    }

    public CourseOutlineDto adminOutline(Course course) {
        return new CourseOutlineDto(adminCourse(course), modules(course.getId(), null, true), false, 0, null);
    }

    public CourseOutlineDto learnerOutline(Course course, Long userId) {
        UUID courseId = course.getId();
        Map<UUID, LessonProgress> progress = learningService.progress(userId, courseId);
        Enrollment enrollment = learningService.enrollment(userId, courseId).orElse(null);
        List<Lesson> ordered = structureService.orderedLessons(courseId);

        long done = ordered.stream().filter(l -> progress.containsKey(l.getId()) && progress.get(l.getId()).isCompleted()).count();
        int percent = LearningService.percent(done, ordered.size());
        CourseDto dto = CourseDto.forLearner(course, moduleRepository.countByCourseId(courseId), ordered.size(),
                enrollment != null, percent);
        return new CourseOutlineDto(dto, modules(courseId, progress, false), enrollment != null, percent,
                resumeLesson(enrollment, ordered, progress));
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
                        .map(l -> lessonDto(l, progress, admin)).toList());
    }

    private List<ModuleDto> modules(UUID courseId, Map<UUID, LessonProgress> progress, boolean admin) {
        Map<UUID, List<Lesson>> byModule = lessonRepository.findByCourseId(courseId).stream()
                .collect(Collectors.groupingBy(Lesson::getModuleId));
        return structureService.modules(courseId).stream()
                .map(m -> moduleDto(m, byModule.getOrDefault(m.getId(), List.of()), progress, admin))
                .toList();
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
