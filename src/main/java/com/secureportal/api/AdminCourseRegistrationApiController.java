package com.secureportal.api;

import com.secureportal.api.dto.RegistrationRequestDto;
import com.secureportal.audit.AuditService;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.CourseRegistrationRequest;
import com.secureportal.course.CourseRegistrationService;
import com.secureportal.course.CourseRepository;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reviewing "request access" submissions for REGISTER-type courses. */
@RestController
@RequestMapping("/api/admin/registrations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCourseRegistrationApiController {

    private final CourseRegistrationService registrationService;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AdminCourseRegistrationApiController(CourseRegistrationService registrationService, CourseRepository courseRepository,
                                                 UserRepository userRepository, AuditService auditService) {
        this.registrationService = registrationService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping
    public List<RegistrationRequestDto> list(@RequestParam(required = false) String status) {
        List<CourseRegistrationRequest> requests = "PENDING".equalsIgnoreCase(status)
                ? registrationService.pending() : registrationService.all();
        return assemble(requests);
    }

    public record DecisionRequest(@Size(max = 1000) String note) {
    }

    @PostMapping("/{id}/approve")
    public RegistrationRequestDto approve(@PathVariable Long id, @RequestBody(required = false) DecisionRequest body,
                                          @AuthenticationPrincipal AppPrincipal principal) {
        CourseRegistrationRequest r = registrationService.approve(id, principal.getEmail(), body == null ? null : body.note());
        auditService.log(principal.getEmail(), "REGISTRATION_APPROVE", r.getCourseId(),
                "Approved user " + r.getUserId() + "'s request");
        return assemble(List.of(r)).get(0);
    }

    @PostMapping("/{id}/deny")
    public RegistrationRequestDto deny(@PathVariable Long id, @RequestBody(required = false) DecisionRequest body,
                                       @AuthenticationPrincipal AppPrincipal principal) {
        CourseRegistrationRequest r = registrationService.deny(id, principal.getEmail(), body == null ? null : body.note());
        auditService.log(principal.getEmail(), "REGISTRATION_DENY", r.getCourseId(),
                "Denied user " + r.getUserId() + "'s request");
        return assemble(List.of(r)).get(0);
    }

    private List<RegistrationRequestDto> assemble(List<CourseRegistrationRequest> requests) {
        Set<UUID> courseIds = requests.stream().map(CourseRegistrationRequest::getCourseId).collect(Collectors.toSet());
        Set<Long> userIds = requests.stream().map(CourseRegistrationRequest::getUserId).collect(Collectors.toSet());
        Map<UUID, String> titles = new HashMap<>();
        courseRepository.findAllById(courseIds).forEach(c -> titles.put(c.getId(), c.getTitle()));
        Map<Long, User> users = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> users.put(u.getId(), u));
        return requests.stream().map(r -> {
            User u = users.get(r.getUserId());
            return RegistrationRequestDto.of(r, titles.get(r.getCourseId()),
                    u == null ? null : u.getDisplayName(), u == null ? null : u.getEmail());
        }).toList();
    }
}
