package com.secureportal.api;

import com.secureportal.api.dto.AdminInterviewDetailDto;
import com.secureportal.api.dto.InterviewAnalyticsDto;
import com.secureportal.interview.MockInterviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admins see how mock interviews are being used, and can open any one to review it. */
@RestController
@RequestMapping("/api/admin/interviews")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMockInterviewApiController {

    private final MockInterviewService interviewService;

    public AdminMockInterviewApiController(MockInterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/analytics")
    public InterviewAnalyticsDto analytics() {
        return InterviewAnalyticsDto.of(interviewService.analytics());
    }

    @GetMapping("/sessions/{sessionId}")
    public AdminInterviewDetailDto session(@PathVariable Long sessionId) {
        return AdminInterviewDetailDto.of(interviewService.adminDetail(sessionId));
    }
}
