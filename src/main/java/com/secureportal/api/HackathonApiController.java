package com.secureportal.api;

import com.secureportal.api.dto.HackathonDto;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.AdminNotALearnerException;
import com.secureportal.hackathon.HackathonService;
import com.secureportal.hackathon.HackathonService.Registration;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Hackathons for signed-in members to browse and register for. */
@RestController
@RequestMapping("/api/hackathons")
public class HackathonApiController {

    public record RegistrationDto(boolean success, boolean alreadyRegistered, int pointsEarned, String message, String registrationUrl) {
        static RegistrationDto of(Registration registration) {
            String message = registration.alreadyRegistered()
                    ? "Already registered for this hackathon"
                    : registration.pointsEarned() > 0
                            ? "Registered successfully! Earned +" + registration.pointsEarned() + " XP."
                            : "Registered successfully!";
            return new RegistrationDto(true, registration.alreadyRegistered(), registration.pointsEarned(), message,
                    registration.registrationUrl());
        }
    }

    private final HackathonService hackathonService;

    public HackathonApiController(HackathonService hackathonService) {
        this.hackathonService = hackathonService;
    }

    @GetMapping
    public List<HackathonDto> list(@RequestParam(defaultValue = "all") String stream, @RequestParam(defaultValue = "all") String mode,
                                   @AuthenticationPrincipal AppPrincipal principal) {
        return hackathonService.list(stream, mode, principal.getUserId()).stream().map(HackathonDto::of).toList();
    }

    @PostMapping("/{id}/register")
    public RegistrationDto register(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
        return RegistrationDto.of(hackathonService.register(principal.getUserId(), id));
    }
}
