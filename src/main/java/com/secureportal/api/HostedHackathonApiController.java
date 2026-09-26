package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.AdminNotALearnerException;
import com.secureportal.hackathon.HostedHackathonService;
import com.secureportal.hackathon.HostedHackathonService.RankedView;
import com.secureportal.hackathon.HostedHackathonService.SubmissionInput;
import com.secureportal.hackathon.HostedHackathonService.SubmissionView;
import com.secureportal.hackathon.HostedHackathonService.TeamView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Teams, submissions and results of hackathons hosted on the platform, for learners. */
@RestController
@RequestMapping("/api/hackathons")
public class HostedHackathonApiController {

    public record TeamRequest(String name, String track) {
    }

    public record JoinRequest(String inviteCode) {
    }

    private final HostedHackathonService hosted;

    public HostedHackathonApiController(HostedHackathonService hosted) {
        this.hosted = hosted;
    }

    /** The learner's own team, or 204 when they haven't joined one. */
    @GetMapping("/{id}/team")
    public ResponseEntity<TeamView> myTeam(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        return hosted.myTeam(id, principal.getUserId()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/team")
    public TeamView createTeam(@PathVariable Long id, @RequestBody TeamRequest request, @AuthenticationPrincipal AppPrincipal principal) {
        requireLearner(principal);
        return hosted.createTeam(id, principal.getUserId(), request.name(), request.track());
    }

    @PostMapping("/join")
    public TeamView join(@RequestBody JoinRequest request, @AuthenticationPrincipal AppPrincipal principal) {
        requireLearner(principal);
        return hosted.joinByCode(principal.getUserId(), request.inviteCode());
    }

    @DeleteMapping("/{id}/team")
    public void leave(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        requireLearner(principal);
        hosted.leave(id, principal.getUserId());
    }

    @PutMapping("/{id}/submission")
    public SubmissionView submit(@PathVariable Long id, @RequestBody SubmissionInput input, @AuthenticationPrincipal AppPrincipal principal) {
        requireLearner(principal);
        return hosted.submit(id, principal.getUserId(), input);
    }

    /** Published results, open to every signed-in member. */
    @GetMapping("/{id}/results")
    public List<RankedView> results(@PathVariable Long id) {
        return hosted.results(id);
    }

    private static void requireLearner(AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
    }
}
