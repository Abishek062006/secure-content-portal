package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.interview.MockInterviewSession;
import com.secureportal.interview.MockInterviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/interviews")
public class MockInterviewApiController {

    private final MockInterviewService interviewService;

    public MockInterviewApiController(MockInterviewService interviewService) {
        this.interviewService = interviewService;
    }

    public record StartSessionRequest(
            String track,       // "STUDENT", "WORKING_PROFESSIONAL"
            String stream,      // "Engineering & Web Dev", "AI & Data Science", etc.
            String difficulty   // "EASY", "MEDIUM", "HARD"
    ) {}

    public record SubmitAnswerRequest(
            Long questionId,
            String learnerAnswer
    ) {}

    @PostMapping("/start")
    public MockInterviewSession startSession(
            @RequestBody StartSessionRequest request,
            @AuthenticationPrincipal AppPrincipal principal) {
        Long userId = principal.getUserId();
        return interviewService.startSession(userId, request.track(), request.stream(), request.difficulty());
    }

    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> getSessionDetails(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal AppPrincipal principal) {
        Long userId = principal.getUserId();
        return interviewService.getSessionDetails(sessionId, userId);
    }

    @PostMapping("/sessions/{sessionId}/answer")
    public Map<String, Object> submitAnswer(
            @PathVariable Long sessionId,
            @RequestBody SubmitAnswerRequest request,
            @AuthenticationPrincipal AppPrincipal principal) {
        Long userId = principal.getUserId();
        return interviewService.submitAnswer(sessionId, userId, request.questionId(), request.learnerAnswer());
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public Map<String, Object> completeSession(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal AppPrincipal principal) {
        Long userId = principal.getUserId();
        return interviewService.completeSession(sessionId, userId);
    }

    @GetMapping("/history")
    public List<MockInterviewSession> getUserHistory(@AuthenticationPrincipal AppPrincipal principal) {
        Long userId = principal.getUserId();
        return interviewService.getUserHistory(userId);
    }
}
