package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.hackathon.HackathonService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hackathons")
public class HackathonApiController {

    private final HackathonService hackathonService;

    public HackathonApiController(HackathonService hackathonService) {
        this.hackathonService = hackathonService;
    }

    @GetMapping
    public List<Map<String, Object>> getHackathons(
            @RequestParam(required = false, defaultValue = "all") String stream,
            @RequestParam(required = false, defaultValue = "all") String mode,
            @AuthenticationPrincipal AppPrincipal principal) {
        Long userId = principal != null ? principal.getUserId() : null;
        return hackathonService.getHackathonsForUser(stream, mode, userId);
    }

    @PostMapping("/{id}/register")
    public Map<String, Object> registerForHackathon(
            @PathVariable Long id,
            @AuthenticationPrincipal AppPrincipal principal) {
        Long userId = principal.getUserId();
        return hackathonService.registerUserForHackathon(userId, id);
    }
}
