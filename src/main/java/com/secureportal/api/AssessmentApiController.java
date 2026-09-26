package com.secureportal.api;

import com.secureportal.api.dto.AttemptDto;
import com.secureportal.api.dto.AttemptSummaryDto;
import com.secureportal.assessment.AttemptService;
import com.secureportal.auth.AppPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The learner taking a quiz or assessment. Access rules and grading live in {@link AttemptService}. */
@RestController
@RequestMapping("/api")
public class AssessmentApiController {

    private final AttemptService attemptService;
    private final AssessmentAssembler assembler;

    public AssessmentApiController(AttemptService attemptService, AssessmentAssembler assembler) {
        this.attemptService = attemptService;
        this.assembler = assembler;
    }

    public record AnswerRequest(@NotNull(message = "Send the question id") UUID questionId,
                                @Min(value = 0, message = "Choose one of the 4 options") @Max(value = 3, message = "Choose one of the 4 options") int optionIndex) {
    }

    /** Starts an attempt — or hands back the one already in progress, so a refresh never costs an attempt. */
    @PostMapping("/assessments/{assessmentId}/attempts")
    public AttemptDto start(@PathVariable UUID assessmentId, @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new com.secureportal.course.AdminNotALearnerException();
        }
        return assembler.attempt(attemptService.start(assessmentId, principal.getUserId(), principal.isAdmin()));
    }

    @GetMapping("/assessments/{assessmentId}/attempts")
    public List<AttemptSummaryDto> history(@PathVariable UUID assessmentId, @AuthenticationPrincipal AppPrincipal principal) {
        return attemptService.history(assessmentId, principal.getUserId()).stream()
                .map(a -> new AttemptSummaryDto(a.getId(), a.getStatus().name(), a.getStartedAt(), a.getSubmittedAt(),
                        a.getScorePercent(), a.getPassed(), a.isTimedOut()))
                .toList();
    }

    @GetMapping("/attempts/{attemptId}")
    public AttemptDto get(@PathVariable UUID attemptId, @AuthenticationPrincipal AppPrincipal principal) {
        return assembler.attempt(attemptService.load(attemptId, principal.getUserId(), principal.isAdmin()));
    }

    @PutMapping("/attempts/{attemptId}/answers")
    public AttemptDto answer(@PathVariable UUID attemptId, @Valid @RequestBody AnswerRequest request,
                             @AuthenticationPrincipal AppPrincipal principal) {
        return assembler.attempt(attemptService.saveAnswer(attemptId, request.questionId(), request.optionIndex(),
                principal.getUserId()));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public AttemptDto submit(@PathVariable UUID attemptId, @AuthenticationPrincipal AppPrincipal principal) {
        return assembler.attempt(attemptService.submit(attemptId, principal.getUserId()));
    }
}
