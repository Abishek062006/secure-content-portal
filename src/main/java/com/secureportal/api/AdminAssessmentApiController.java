package com.secureportal.api;

import com.secureportal.api.dto.AssessmentResultsDto;
import com.secureportal.api.dto.AssessmentSummaryDto;
import com.secureportal.assessment.Assessment;
import com.secureportal.assessment.AssessmentAnalyticsService;
import com.secureportal.assessment.AssessmentInput;
import com.secureportal.assessment.AssessmentService;
import com.secureportal.assessment.AssessmentType;
import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.CourseService;
import com.secureportal.course.CourseStructureService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Set up quizzes and assessments per module (or one final assessment per course), and see how learners do. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAssessmentApiController {

    private final AssessmentService assessmentService;
    private final AssessmentAnalyticsService analyticsService;
    private final AssessmentAssembler assembler;
    private final CourseStructureService structureService;
    private final CourseService courseService;
    private final AuditService auditService;

    public AdminAssessmentApiController(AssessmentService assessmentService, AssessmentAnalyticsService analyticsService,
                                        AssessmentAssembler assembler, CourseStructureService structureService,
                                        CourseService courseService, AuditService auditService) {
        this.assessmentService = assessmentService;
        this.analyticsService = analyticsService;
        this.assembler = assembler;
        this.structureService = structureService;
        this.courseService = courseService;
        this.auditService = auditService;
    }

    public record AssessmentRequest(
            @NotNull(message = "Choose quiz or assessment") AssessmentType type,
            @NotBlank(message = "Give it a title") @Size(max = 200, message = "The title must be 200 characters or fewer") String title,
            @Min(value = 0, message = "Counts can't be negative") @Max(value = 100, message = "At most 100 questions per difficulty") int easyCount,
            @Min(value = 0, message = "Counts can't be negative") @Max(value = 100, message = "At most 100 questions per difficulty") int mediumCount,
            @Min(value = 0, message = "Counts can't be negative") @Max(value = 100, message = "At most 100 questions per difficulty") int hardCount,
            Integer passPercent,
            Integer timeLimitMinutes,
            Integer maxAttempts,
            boolean gatesNext,
            Integer reusePercent
    ) {
        AssessmentInput toInput() {
            return new AssessmentInput(type, title, easyCount, mediumCount, hardCount, passPercent, timeLimitMinutes,
                    maxAttempts, gatesNext, reusePercent);
        }
    }

    @PutMapping("/modules/{moduleId}/assessment")
    public AssessmentSummaryDto saveForModule(@PathVariable UUID moduleId, @Valid @RequestBody AssessmentRequest request,
                                              @AuthenticationPrincipal AppPrincipal principal) {
        Assessment saved = assessmentService.saveForModule(moduleId, request.toInput());
        auditService.log(principal.getEmail(), "ASSESSMENT_SAVE", saved.getCourseId(), saved.getType() + " \"" + saved.getTitle() + "\"");
        return assembler.admin(saved);
    }

    @DeleteMapping("/modules/{moduleId}/assessment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteForModule(@PathVariable UUID moduleId, @AuthenticationPrincipal AppPrincipal principal) {
        UUID courseId = structureService.findModule(moduleId).getCourseId();
        assessmentService.deleteForModule(moduleId);
        auditService.log(principal.getEmail(), "ASSESSMENT_DELETE", courseId, "module assessment removed");
    }

    @PutMapping("/courses/{courseId}/final-assessment")
    public AssessmentSummaryDto saveFinal(@PathVariable UUID courseId, @Valid @RequestBody AssessmentRequest request,
                                          @AuthenticationPrincipal AppPrincipal principal) {
        Assessment saved = assessmentService.saveFinal(courseId, request.toInput());
        auditService.log(principal.getEmail(), "ASSESSMENT_SAVE", courseId, "final " + saved.getType() + " \"" + saved.getTitle() + "\"");
        return assembler.admin(saved);
    }

    @DeleteMapping("/courses/{courseId}/final-assessment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFinal(@PathVariable UUID courseId, @AuthenticationPrincipal AppPrincipal principal) {
        assessmentService.deleteFinal(courseId);
        auditService.log(principal.getEmail(), "ASSESSMENT_DELETE", courseId, "final assessment removed");
    }

    @GetMapping("/courses/{courseId}/assessment-results")
    public AssessmentResultsDto results(@PathVariable UUID courseId) {
        courseService.find(courseId);
        AssessmentAnalyticsService.Results results = analyticsService.forCourse(courseId);
        return new AssessmentResultsDto(
                results.assessments().stream().map(s -> new AssessmentResultsDto.AssessmentRow(
                        s.assessment().getId(), s.assessment().getTitle(), s.assessment().getType().name(), s.moduleTitle(),
                        s.attempts(), s.learners(),
                        s.averageScore() == null ? null : (int) Math.round(s.averageScore()),
                        s.passRate() == null ? null : (int) Math.round(100 * s.passRate()))).toList(),
                results.hardestQuestions().stream().map(q -> new AssessmentResultsDto.HardQuestion(
                        q.question().getId(), q.question().getText(), q.question().getDifficulty().name(), q.lessonTitle(),
                        q.answered(), (int) Math.round(100 * q.correctRate()))).toList());
    }
}
