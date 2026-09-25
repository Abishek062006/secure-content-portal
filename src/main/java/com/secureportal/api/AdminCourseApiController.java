package com.secureportal.api;

import com.secureportal.api.dto.CourseDto;
import com.secureportal.api.dto.CourseOutlineDto;
import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.content.dto.EditForm;
import com.secureportal.course.Course;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.CourseService;
import com.secureportal.course.CourseStatus;
import com.secureportal.course.dto.CourseCreateForm;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** URL-level access is enforced by SecurityConfig; {@code @PreAuthorize} is the independent method-level second check. */
@RestController
@RequestMapping("/api/admin/courses")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCourseApiController {

    private final CourseRepository courseRepository;
    private final CourseService courseService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final CourseOutlineAssembler assembler;

    public AdminCourseApiController(CourseRepository courseRepository, CourseService courseService,
                                    UserRepository userRepository, AuditService auditService,
                                    CourseOutlineAssembler assembler) {
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.assembler = assembler;
    }

    @GetMapping
    public List<CourseDto> list() {
        return assembler.adminCourses(courseRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    public CourseOutlineDto outline(@PathVariable UUID id) {
        return assembler.adminOutline(courseService.find(id));
    }

    @PostMapping
    public CourseDto create(@Valid @ModelAttribute CourseCreateForm form, @AuthenticationPrincipal AppPrincipal principal) {
        User createdBy = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new IllegalStateException("Signed-in user not found: " + principal.getUserId()));
        Course created = courseService.create(form, createdBy);
        auditService.log(principal.getEmail(), "COURSE_CREATE", created.getId(), "\"" + created.getTitle() + "\"");
        return assembler.adminCourse(created);
    }

    @PutMapping("/{id}")
    public CourseDto update(@PathVariable UUID id, @Valid @RequestBody EditForm form,
                            @AuthenticationPrincipal AppPrincipal principal) {
        Course updated = courseService.update(id, form);
        auditService.log(principal.getEmail(), "COURSE_EDIT", id, "\"" + updated.getTitle() + "\"");
        return assembler.adminCourse(updated);
    }

    @PostMapping("/{id}/thumbnail")
    public CourseDto replaceThumbnail(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                      @AuthenticationPrincipal AppPrincipal principal) {
        Course updated = courseService.replaceThumbnail(id, file);
        auditService.log(principal.getEmail(), "COURSE_EDIT", id, "cover of \"" + updated.getTitle() + "\"");
        return assembler.adminCourse(updated);
    }

    @PostMapping("/{id}/publish")
    public CourseDto publish(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        Course course = courseService.setStatus(id, CourseStatus.PUBLISHED);
        auditService.log(principal.getEmail(), "COURSE_PUBLISH", id, "\"" + course.getTitle() + "\"");
        return assembler.adminCourse(course);
    }

    @PostMapping("/{id}/unpublish")
    public CourseDto unpublish(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        Course course = courseService.setStatus(id, CourseStatus.DRAFT);
        auditService.log(principal.getEmail(), "COURSE_UNPUBLISH", id, "\"" + course.getTitle() + "\"");
        return assembler.adminCourse(course);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        String title = courseService.find(id).getTitle();
        courseService.delete(id);
        auditService.log(principal.getEmail(), "COURSE_DELETE", id, "\"" + title + "\"");
    }
}
