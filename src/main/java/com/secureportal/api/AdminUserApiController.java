package com.secureportal.api;

import com.secureportal.api.dto.UserDto;
import com.secureportal.auth.AppPrincipal;
import com.secureportal.user.UserRepository;
import com.secureportal.user.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserApiController {

    private static final int PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final UserService userService;
    private final com.secureportal.notification.NotificationService notificationService;

    public AdminUserApiController(UserRepository userRepository, UserService userService,
                                  com.secureportal.notification.NotificationService notificationService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<UserDto> list(@RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.ASC, "email"));
        String query = (search == null) ? "" : search;
        return userRepository.search(query, pageable).getContent().stream()
                .map(UserDto::from)
                .toList();
    }

    @PostMapping("/{id}/promote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void promote(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        userService.promoteToAdmin(id, principal.getEmail());
        try {
            notificationService.createNotification(
                    id,
                    com.secureportal.notification.NotificationCategory.SECURITY,
                    "Role Promoted to Administrator",
                    "Your account has been elevated to Administrator by " + principal.getEmail() + ".",
                    com.secureportal.notification.NotificationPriority.IMPORTANT,
                    "/admin/courses"
            );
        } catch (Exception ignored) {
        }
    }

    @PostMapping("/{id}/demote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void demote(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        userService.demoteToViewer(id, principal.getEmail(), principal.getUserId());
        try {
            notificationService.createNotification(
                    id,
                    com.secureportal.notification.NotificationCategory.SECURITY,
                    "Role Updated to Learner",
                    "Your account role has been updated to Viewer/Learner by " + principal.getEmail() + ".",
                    com.secureportal.notification.NotificationPriority.NORMAL,
                    "/courses"
            );
        } catch (Exception ignored) {
        }
    }
}
