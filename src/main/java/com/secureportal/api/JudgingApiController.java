package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.hackathon.Hackathon;
import com.secureportal.hackathon.HostedHackathonService;
import com.secureportal.hackathon.HostedHackathonService.ProjectView;
import com.secureportal.hackathon.HostedHackathonService.ScoreInput;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** What a judge sees: the events they were asked to judge and the projects to score. Only assigned judges get through. */
@RestController
@RequestMapping("/api/judging")
public class JudgingApiController {

    public record JudgedEvent(Long id, String title, String phase) {
        static JudgedEvent of(Hackathon h) {
            return new JudgedEvent(h.getId(), h.getTitle(), h.phase(java.time.Instant.now()).name());
        }
    }

    private final HostedHackathonService hosted;

    public JudgingApiController(HostedHackathonService hosted) {
        this.hosted = hosted;
    }

    @GetMapping("/events")
    public List<JudgedEvent> events(@AuthenticationPrincipal AppPrincipal principal) {
        return hosted.judgedEvents(principal.getUserId()).stream().map(JudgedEvent::of).toList();
    }

    @GetMapping("/hackathons/{id}/submissions")
    public List<ProjectView> submissions(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        return hosted.projectsForJudge(id, principal.getUserId());
    }

    @PutMapping("/hackathons/{id}/submissions/{submissionId}/score")
    public void score(@PathVariable Long id, @PathVariable Long submissionId, @RequestBody ScoreInput input,
                      @AuthenticationPrincipal AppPrincipal principal) {
        hosted.score(id, submissionId, principal.getUserId(), input);
    }
}
