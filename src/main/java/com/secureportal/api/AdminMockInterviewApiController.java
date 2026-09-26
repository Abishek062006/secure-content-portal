package com.secureportal.api;

import com.secureportal.interview.MockInterviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/interviews")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMockInterviewApiController {

    private final MockInterviewService interviewService;

    public AdminMockInterviewApiController(MockInterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/analytics")
    public Map<String, Object> getAnalytics() {
        return interviewService.getAdminAnalytics();
    }

    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> getSessionDetailsForAdmin(@PathVariable Long sessionId) {
        return interviewService.getSessionDetailsForAdmin(sessionId);
    }
}
