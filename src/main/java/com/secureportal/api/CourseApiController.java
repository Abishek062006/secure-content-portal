package com.secureportal.api;

import com.secureportal.api.dto.CourseDto;
import com.secureportal.api.dto.CourseOutlineDto;
import com.secureportal.api.dto.LessonDetailResponse;
import com.secureportal.assessment.AssessmentAccess;
import com.secureportal.assessment.AssessmentAccessException;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.Course;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.CourseService;
import com.secureportal.course.CourseStatus;
import com.secureportal.course.CourseStructureService;
import com.secureportal.course.Enrollment;
import com.secureportal.course.EnrollmentRepository;
import com.secureportal.course.EnrollmentRequiredException;
import com.secureportal.course.LearningService;
import com.secureportal.course.Lesson;
import com.secureportal.course.LessonProgress;
import com.secureportal.course.LessonProgressRepository;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import com.secureportal.stream.StreamTicket;
import com.secureportal.stream.StreamTicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The learner-facing side of courses: catalog, outline, enrolling, the lesson player, and progress. */
@RestController
@RequestMapping("/api/courses")
public class CourseApiController {

    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final CourseStructureService structureService;
    private final LearningService learningService;
    private final EnrollmentRepository enrollmentRepository;
    private final LessonProgressRepository progressRepository;
    private final CourseOutlineAssembler assembler;
    private final StreamTicketService ticketService;
    private final StorageService storageService;
    private final AssessmentAccess assessmentAccess;

    public CourseApiController(CourseRepository courseRepository, CourseService courseService,
                               CourseStructureService structureService, LearningService learningService,
                               EnrollmentRepository enrollmentRepository, LessonProgressRepository progressRepository,
                               CourseOutlineAssembler assembler, StreamTicketService ticketService,
                               StorageService storageService, AssessmentAccess assessmentAccess) {
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.structureService = structureService;
        this.learningService = learningService;
        this.enrollmentRepository = enrollmentRepository;
        this.progressRepository = progressRepository;
        this.assembler = assembler;
        this.ticketService = ticketService;
        this.storageService = storageService;
        this.assessmentAccess = assessmentAccess;
    }

    public record ProgressRequest(@Min(0) int positionSeconds, boolean completed) {
    }

    public record ProgressResponse(boolean completed, int progressPercent) {
    }

    @GetMapping
    public List<CourseDto> list(@AuthenticationPrincipal AppPrincipal principal) {
        Set<UUID> enrolled = new HashSet<>();
        for (Enrollment e : enrollmentRepository.findByUserId(principal.getUserId())) {
            enrolled.add(e.getCourseId());
        }
        return assembler.catalog(courseRepository.findByStatusOrderByCreatedAtDesc(CourseStatus.PUBLISHED),
                principal.getUserId(), progressRepository.countCompletedPerCourse(principal.getUserId()), enrolled);
    }

    @GetMapping("/{id}")
    public CourseOutlineDto outline(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        return assembler.learnerOutline(learningService.visibleCourse(id, principal.isAdmin()), principal.getUserId(), principal.isAdmin());
    }

    @PostMapping("/{id}/enroll")
    public CourseOutlineDto enroll(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        Course course = learningService.visibleCourse(id, principal.isAdmin());
        learningService.enroll(principal.getUserId(), course);
        return assembler.learnerOutline(course, principal.getUserId(), principal.isAdmin());
    }

    /** Cover images sit behind sign-in like everything else, but aren't ticketed: they're catalog art. */
    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<byte[]> thumbnail(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        Course course = learningService.visibleCourse(id, principal.isAdmin());
        if (course.getThumbnailKey() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try (StorageObject object = storageService.get(course.getThumbnailKey(), null, null)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(course.getThumbnailMime()))
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(object.content().readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read thumbnail for course " + id, e);
        }
    }

    @GetMapping("/{courseId}/lessons/{lessonId}")
    public LessonDetailResponse lesson(@PathVariable UUID courseId, @PathVariable UUID lessonId,
                                       @AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) {
        Course course = learningService.visibleCourse(courseId, principal.isAdmin());
        Lesson lesson = learningService.lessonOf(courseId, lessonId);

        Enrollment enrollment = learningService.enrollment(principal.getUserId(), courseId).orElse(null);
        if (enrollment == null && !principal.isAdmin()) {
            throw new EnrollmentRequiredException();
        }
        if (!principal.isAdmin()) {
            String locked = assessmentAccess.lockedModules(courseId, principal.getUserId()).get(lesson.getModuleId());
            if (locked != null) {
                throw new AssessmentAccessException(locked);
            }
        }
        if (enrollment != null) {
            learningService.visit(enrollment, lessonId);
        }
        courseService.recordView(courseId);

        List<Lesson> ordered = structureService.orderedLessons(courseId);
        int index = 0;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(lessonId)) {
                index = i;
            }
        }
        Map<UUID, LessonProgress> progress = learningService.progress(principal.getUserId(), courseId);
        LessonProgress mine = progress.get(lessonId);
        boolean completed = mine != null && mine.isCompleted();
        int resume = mine != null && !completed && mine.getPositionSeconds() > 5 ? mine.getPositionSeconds() : 0;

        String ticket = ticketService.mint(lessonId, principal.getUserId(), request,
                StreamTicket.Purpose.COURSE_VIDEO, Duration.ofMinutes(30));
        var module = structureService.findModule(lesson.getModuleId());
        return new LessonDetailResponse(courseId, course.getTitle(), module.getId(), module.getTitle(),
                assembler.lessonDto(lesson, progress, principal.isAdmin()), ticket, structureService.transcript(lesson),
                index > 0 ? ordered.get(index - 1).getId() : null,
                index < ordered.size() - 1 ? ordered.get(index + 1).getId() : null, resume, completed);
    }

    @PutMapping("/{courseId}/lessons/{lessonId}/progress")
    public ProgressResponse saveProgress(@PathVariable UUID courseId, @PathVariable UUID lessonId,
                                         @Valid @RequestBody ProgressRequest body,
                                         @AuthenticationPrincipal AppPrincipal principal) {
        learningService.visibleCourse(courseId, principal.isAdmin());
        Lesson lesson = learningService.lessonOf(courseId, lessonId);
        if (learningService.enrollment(principal.getUserId(), courseId).isEmpty()) {
            if (principal.isAdmin()) {
                return new ProgressResponse(false, 0);
            }
            throw new EnrollmentRequiredException();
        }
        learningService.saveProgress(principal.getUserId(), lesson, body.positionSeconds(), body.completed());
        boolean completed = learningService.progress(principal.getUserId(), courseId).get(lessonId).isCompleted();
        return new ProgressResponse(completed, learningService.progressPercent(principal.getUserId(), courseId));
    }
}
