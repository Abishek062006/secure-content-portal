package com.secureportal.api;

import com.secureportal.hackathon.Hackathon;
import com.secureportal.hackathon.HackathonService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/hackathons")
@PreAuthorize("hasRole('ADMIN')")
public class AdminHackathonApiController {

    private final HackathonService hackathonService;

    public AdminHackathonApiController(HackathonService hackathonService) {
        this.hackathonService = hackathonService;
    }

    @GetMapping
    public List<Map<String, Object>> getAdminHackathons(
            @RequestParam(required = false, defaultValue = "all") String stream,
            @RequestParam(required = false, defaultValue = "all") String mode) {
        return hackathonService.getHackathonsForUser(stream, mode, null);
    }

    @PostMapping
    public Hackathon createHackathon(@RequestBody Hackathon hackathon) {
        return hackathonService.createHackathon(hackathon);
    }

    @PutMapping("/{id}")
    public Hackathon updateHackathon(@PathVariable Long id, @RequestBody Hackathon hackathon) {
        return hackathonService.updateHackathon(id, hackathon);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteHackathon(@PathVariable Long id) {
        hackathonService.deleteHackathon(id);
        return Map.of("success", true, "message", "Hackathon deleted successfully");
    }
}
