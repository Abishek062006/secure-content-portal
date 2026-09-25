package com.secureportal.api;

import com.secureportal.api.dto.CourseDto;
import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.content.dto.EditForm;
import com.secureportal.course.Course;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.CourseService;
import com.secureportal.course.dto.CourseUploadForm;
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

    public AdminCourseApiController(CourseRepository courseRepository, CourseService courseService,
                                    UserRepository userRepository, AuditService auditService) {
        this.courseRepository = courseRepository;
        this.courseService = courseService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public List<CourseDto> list() {
        return courseRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(CourseDto::forAdmin)
                .toList();
    }

    @PostMapping
    public CourseDto create(@Valid @ModelAttribute CourseUploadForm form,
                            @AuthenticationPrincipal AppPrincipal principal) {
        User uploadedBy = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new IllegalStateException("Signed-in user not found: " + principal.getUserId()));

        Course created = courseService.create(form, uploadedBy);
        auditService.log(principal.getEmail(), "COURSE_UPLOAD", created.getId(), "\"" + created.getTitle() + "\"");
        return CourseDto.forAdmin(created);
    }

    @PutMapping("/{id}")
    public CourseDto update(@PathVariable UUID id, @Valid @RequestBody EditForm form,
                            @AuthenticationPrincipal AppPrincipal principal) {
        Course updated = courseService.update(id, form);
        auditService.log(principal.getEmail(), "COURSE_EDIT", id, "\"" + updated.getTitle() + "\"");
        return CourseDto.forAdmin(updated);
    }

    @PostMapping("/{id}/thumbnail")
    public CourseDto replaceThumbnail(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                      @AuthenticationPrincipal AppPrincipal principal) {
        Course updated = courseService.replaceThumbnail(id, file);
        auditService.log(principal.getEmail(), "COURSE_EDIT", id, "thumbnail of \"" + updated.getTitle() + "\"");
        return CourseDto.forAdmin(updated);
    }

    @PostMapping("/{id}/transcript")
    public CourseDto replaceTranscript(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
                                       @AuthenticationPrincipal AppPrincipal principal) {
        Course updated = courseService.replaceTranscript(id, file);
        auditService.log(principal.getEmail(), "COURSE_EDIT", id, "transcript of \"" + updated.getTitle() + "\"");
        return CourseDto.forAdmin(updated);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal AppPrincipal principal) {
        String title = courseService.find(id).getTitle();
        courseService.delete(id);
        auditService.log(principal.getEmail(), "COURSE_DELETE", id, "\"" + title + "\"");
    }
}
