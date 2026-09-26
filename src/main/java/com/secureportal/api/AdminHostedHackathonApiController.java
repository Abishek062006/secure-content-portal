package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.hackathon.HostedHackathonService;
import com.secureportal.hackathon.HostedHackathonService.JudgeView;
import com.secureportal.hackathon.HostedHackathonService.ProjectView;
import com.secureportal.hackathon.HostedHackathonService.RankedView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admins run a hosted hackathon: pick the judges, watch the scoring, publish the results. Every change is audited. */
@RestController
@RequestMapping("/api/admin/hackathons/{id}")
@PreAuthorize("hasRole('ADMIN')")
public class AdminHostedHackathonApiController {

    public record JudgeRequest(String email) {
    }

    private final HostedHackathonService hosted;

    public AdminHostedHackathonApiController(HostedHackathonService hosted) {
        this.hosted = hosted;
    }

    @GetMapping("/judges")
    public List<JudgeView> judges(@PathVariable Long id) {
        return hosted.judgesOf(id);
    }

    @PostMapping("/judges")
    @ResponseStatus(HttpStatus.CREATED)
    public JudgeView addJudge(@PathVariable Long id, @RequestBody JudgeRequest request, @AuthenticationPrincipal AppPrincipal admin) {
        return hosted.addJudge(id, request.email(), admin.getEmail());
    }

    @DeleteMapping("/judges/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeJudge(@PathVariable Long id, @PathVariable Long userId, @AuthenticationPrincipal AppPrincipal admin) {
        hosted.removeJudge(id, userId, admin.getEmail());
    }

    @GetMapping("/submissions")
    public List<ProjectView> submissions(@PathVariable Long id) {
        return hosted.projectsForAdmin(id);
    }

    @GetMapping("/standings")
    public List<RankedView> standings(@PathVariable Long id) {
        return hosted.standings(id);
    }

    @PostMapping("/publish")
    public void publish(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal admin) {
        hosted.publishResults(id, admin.getEmail());
    }
}
