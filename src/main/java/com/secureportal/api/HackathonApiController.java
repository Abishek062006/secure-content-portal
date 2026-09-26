package com.secureportal.api;

import com.secureportal.api.dto.HackathonDto;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.AdminNotALearnerException;
import com.secureportal.hackathon.HackathonCalendar;
import com.secureportal.hackathon.HackathonService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Hackathons for signed-in members to browse, save and add to a calendar. Registration happens on the organiser's own site. */
@RestController
@RequestMapping("/api/hackathons")
public class HackathonApiController {

    private final HackathonService hackathonService;

    public HackathonApiController(HackathonService hackathonService) {
        this.hackathonService = hackathonService;
    }

    @GetMapping
    public List<HackathonDto> list(@RequestParam(defaultValue = "all") String stream, @RequestParam(defaultValue = "all") String mode,
                                   @RequestParam(defaultValue = "false") boolean saved, @AuthenticationPrincipal AppPrincipal principal) {
        return hackathonService.list(stream, mode, saved, principal.getUserId()).stream().map(HackathonDto::of).toList();
    }

    @PutMapping("/{id}/save")
    public void save(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
        hackathonService.save(principal.getUserId(), id);
    }

    @DeleteMapping("/{id}/save")
    public void unsave(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
        hackathonService.unsave(principal.getUserId(), id);
    }

    @GetMapping("/{id}/calendar.ics")
    public ResponseEntity<byte[]> calendar(@PathVariable Long id) {
        byte[] body = HackathonCalendar.ics(hackathonService.find(id)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("hackathon-" + id + ".ics").build().toString())
                .body(body);
    }
}
