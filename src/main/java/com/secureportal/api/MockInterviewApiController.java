package com.secureportal.api;

import com.secureportal.api.dto.InterviewDetailDto;
import com.secureportal.api.dto.InterviewQuestionDto;
import com.secureportal.api.dto.InterviewSessionDto;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.AdminNotALearnerException;
import com.secureportal.interview.AnswerDelivery;
import com.secureportal.interview.MockInterviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** AI mock interviews for learners. Each interview is private to the learner who started it. */
@RestController
@RequestMapping("/api/interviews")
public class MockInterviewApiController {

    /** {@code delivery} is optional: how the answer was given, as the browser measured it. */
    public record AnswerRequest(Long questionId, String learnerAnswer, AnswerDelivery delivery) {
    }

    public record AnswerResponse(InterviewQuestionDto question, InterviewSessionDto session, InterviewQuestionDto followUp) {
    }

    public record CompletionResponse(InterviewSessionDto session, List<InterviewQuestionDto> questions, int xpEarned, String message) {
    }

    private final MockInterviewService interviewService;

    public MockInterviewApiController(MockInterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping("/start")
    public InterviewSessionDto start(@RequestBody MockInterviewService.StartRequest request, @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
        return InterviewSessionDto.of(interviewService.start(principal.getUserId(), request));
    }

    @PostMapping("/sessions/{sessionId}/retry")
    public InterviewSessionDto retry(@PathVariable Long sessionId, @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
        return InterviewSessionDto.of(interviewService.retry(principal.getUserId(), sessionId));
    }

    /** How many interviews the learner has started in the last day, and the daily limit. */
    @GetMapping("/quota")
    public MockInterviewService.Quota quota(@AuthenticationPrincipal AppPrincipal principal) {
        return interviewService.quota(principal.getUserId());
    }

    @GetMapping("/sessions/{sessionId}")
    public InterviewDetailDto session(@PathVariable Long sessionId, @AuthenticationPrincipal AppPrincipal principal) {
        MockInterviewService.Detail detail = interviewService.detail(sessionId, principal.getUserId());
        return InterviewDetailDto.of(detail.session(), detail.questions());
    }

    @PostMapping("/sessions/{sessionId}/answer")
    public AnswerResponse answer(@PathVariable Long sessionId, @RequestBody AnswerRequest request,
                                 @AuthenticationPrincipal AppPrincipal principal) {
        MockInterviewService.AnswerResult result = interviewService.submitAnswer(sessionId, principal.getUserId(), request.questionId(),
                request.learnerAnswer(), request.delivery());
        return new AnswerResponse(InterviewQuestionDto.of(result.question()), InterviewSessionDto.of(result.session()),
                result.followUp() == null ? null : InterviewQuestionDto.of(result.followUp()));
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public CompletionResponse complete(@PathVariable Long sessionId, @AuthenticationPrincipal AppPrincipal principal) {
        MockInterviewService.Completion completion = interviewService.complete(sessionId, principal.getUserId());
        String message = completion.xpEarned() > 0
                ? "Mock interview complete! Earned +" + completion.xpEarned() + " XP."
                : "Mock interview complete!";
        return new CompletionResponse(InterviewSessionDto.of(completion.session()),
                completion.questions().stream().map(InterviewQuestionDto::of).toList(), completion.xpEarned(), message);
    }

    /** How the learner's speech timing has moved over their last few interviews, oldest first. */
    @GetMapping("/delivery-trend")
    public List<MockInterviewService.DeliveryPoint> deliveryTrend(@AuthenticationPrincipal AppPrincipal principal) {
        return interviewService.deliveryTrend(principal.getUserId());
    }

    @GetMapping("/history")
    public List<InterviewSessionDto> history(@AuthenticationPrincipal AppPrincipal principal) {
        return interviewService.history(principal.getUserId()).stream().map(InterviewSessionDto::of).toList();
    }
}
