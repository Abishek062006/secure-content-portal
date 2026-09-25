package com.secureportal.api;

import com.secureportal.api.dto.CourseOutlineDto.LessonDto;
import com.secureportal.api.dto.CourseOutlineDto.ModuleDto;
import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.CourseModule;
import com.secureportal.course.CourseStructureService;
import com.secureportal.course.Lesson;
import com.secureportal.course.dto.LessonUploadForm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/** Build a course's outline: add, edit, delete and reorder modules and lessons. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCourseStructureApiController {

    private final CourseStructureService structureService;
    private final CourseOutlineAssembler assembler;
    private final AuditService auditService;

    public AdminCourseStructureApiController(CourseStructureService structureService, CourseOutlineAssembler assembler,
                                             AuditService auditService) {
        this.structureService = structureService;
        this.assembler = assembler;
        this.auditService = auditService;
    }

    public record TitleRequest(
            @NotBlank(message = "Title is required") @Size(max = 200, message = "Title must be 200 characters or fewer") String title,
            @Size(max = 2000, message = "Description must be 2000 characters or fewer") String description) {
    }

    public record OrderRequest(@NotNull(message = "Send the ids in the order you want") List<UUID> ids) {
    }

    // ---- modules ----

    @PostMapping("/courses/{courseId}/modules")
    public ModuleDto addModule(@PathVariable UUID courseId, @Valid @RequestBody TitleRequest body,
                               @AuthenticationPrincipal AppPrincipal principal) {
        CourseModule module = structureService.addModule(courseId, body.title(), body.description());
        auditService.log(principal.getEmail(), "MODULE_ADD", courseId, "\"" + module.getTitle() + "\"");
        return assembler.moduleDto(module, List.of(), null, true);
    }

    @PutMapping("/modules/{moduleId}")
    public ModuleDto editModule(@PathVariable UUID moduleId, @Valid @RequestBody TitleRequest body,
                                @AuthenticationPrincipal AppPrincipal principal) {
        CourseModule module = structureService.editModule(moduleId, body.title(), body.description());
        auditService.log(principal.getEmail(), "MODULE_EDIT", module.getCourseId(), "\"" + module.getTitle() + "\"");
        return assembler.moduleDto(module, List.of(), null, true);
    }

    @DeleteMapping("/modules/{moduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModule(@PathVariable UUID moduleId, @AuthenticationPrincipal AppPrincipal principal) {
        CourseModule module = structureService.findModule(moduleId);
        structureService.deleteModule(moduleId);
        auditService.log(principal.getEmail(), "MODULE_DELETE", module.getCourseId(), "\"" + module.getTitle() + "\"");
    }

    @PutMapping("/courses/{courseId}/modules/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderModules(@PathVariable UUID courseId, @Valid @RequestBody OrderRequest body,
                               @AuthenticationPrincipal AppPrincipal principal) {
        structureService.reorderModules(courseId, body.ids());
        auditService.log(principal.getEmail(), "STRUCTURE_REORDER", courseId, "modules reordered");
    }

    // ---- lessons ----

    @PostMapping("/modules/{moduleId}/lessons")
    public LessonDto addLesson(@PathVariable UUID moduleId, @Valid @ModelAttribute LessonUploadForm form,
                               @AuthenticationPrincipal AppPrincipal principal) {
        Lesson lesson = structureService.addLesson(moduleId, form);
        auditService.log(principal.getEmail(), "LESSON_ADD", lesson.getCourseId(), "\"" + lesson.getTitle() + "\"");
        return assembler.lessonDto(lesson, null, true);
    }

    @PutMapping("/lessons/{lessonId}")
    public LessonDto editLesson(@PathVariable UUID lessonId, @Valid @RequestBody TitleRequest body,
                                @AuthenticationPrincipal AppPrincipal principal) {
        Lesson lesson = structureService.editLesson(lessonId, body.title(), body.description());
        auditService.log(principal.getEmail(), "LESSON_EDIT", lesson.getCourseId(), "\"" + lesson.getTitle() + "\"");
        return assembler.lessonDto(lesson, null, true);
    }

    @PostMapping("/lessons/{lessonId}/transcript")
    public LessonDto replaceTranscript(@PathVariable UUID lessonId, @RequestParam("file") MultipartFile file,
                                       @AuthenticationPrincipal AppPrincipal principal) {
        Lesson lesson = structureService.replaceTranscript(lessonId, file);
        auditService.log(principal.getEmail(), "LESSON_EDIT", lesson.getCourseId(), "transcript of \"" + lesson.getTitle() + "\"");
        return assembler.lessonDto(lesson, null, true);
    }

    @DeleteMapping("/lessons/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLesson(@PathVariable UUID lessonId, @AuthenticationPrincipal AppPrincipal principal) {
        Lesson lesson = structureService.findLesson(lessonId);
        structureService.deleteLesson(lessonId);
        auditService.log(principal.getEmail(), "LESSON_DELETE", lesson.getCourseId(), "\"" + lesson.getTitle() + "\"");
    }

    @PutMapping("/modules/{moduleId}/lessons/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorderLessons(@PathVariable UUID moduleId, @Valid @RequestBody OrderRequest body,
                               @AuthenticationPrincipal AppPrincipal principal) {
        CourseModule module = structureService.findModule(moduleId);
        structureService.reorderLessons(moduleId, body.ids());
        auditService.log(principal.getEmail(), "STRUCTURE_REORDER", module.getCourseId(), "lessons reordered");
    }
}
