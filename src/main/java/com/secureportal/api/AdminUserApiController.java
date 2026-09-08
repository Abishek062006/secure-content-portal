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

    public AdminUserApiController(UserRepository userRepository, UserService userService) {
        this.userRepository = userRepository;
        this.userService = userService;
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
    }

    @PostMapping("/{id}/demote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void demote(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        userService.demoteToViewer(id, principal.getEmail(), principal.getUserId());
    }
}
