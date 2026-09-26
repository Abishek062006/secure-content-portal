package com.secureportal.api;

import com.secureportal.api.dto.HackathonDto;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.hackathon.HackathonService;
import com.secureportal.hackathon.HackathonService.Input;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admins list, edit and remove hackathons. Every change is validated by the service and written to the audit log. */
@RestController
@RequestMapping("/api/admin/hackathons")
@PreAuthorize("hasRole('ADMIN')")
public class AdminHackathonApiController {

    private final HackathonService hackathonService;

    public AdminHackathonApiController(HackathonService hackathonService) {
        this.hackathonService = hackathonService;
    }

    @GetMapping
    public List<HackathonDto> list(@RequestParam(defaultValue = "all") String stream, @RequestParam(defaultValue = "all") String mode) {
        return hackathonService.list(stream, mode, null).stream().map(HackathonDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HackathonDto create(@RequestBody Input input, @AuthenticationPrincipal AppPrincipal admin) {
        return HackathonDto.of(hackathonService.create(input, admin.getEmail()));
    }

    @PutMapping("/{id}")
    public HackathonDto update(@PathVariable Long id, @RequestBody Input input, @AuthenticationPrincipal AppPrincipal admin) {
        return HackathonDto.of(hackathonService.update(id, input, admin.getEmail()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal admin) {
        hackathonService.delete(id, admin.getEmail());
    }
}
