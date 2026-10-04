package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.AdminNotALearnerException;
import com.secureportal.network.ConnectionRequestDto;
import com.secureportal.network.ConnectionService;
import com.secureportal.network.ConnectionUserDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Members finding and connecting with each other. Rules and scoping live in {@link ConnectionService}. */
@RestController
@RequestMapping("/api/network")
public class ConnectionApiController {

    private static final int MAX_PAGE_SIZE = 50;

    private final ConnectionService connectionService;

    public ConnectionApiController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/suggestions")
    public Page<ConnectionUserDto> suggestions(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal AppPrincipal principal) {
        return connectionService.suggestions(principal.getUserId(), q, PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size))));
    }

    @PostMapping("/requests/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectionRequestDto sendRequest(@PathVariable Long userId, @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
        return connectionService.sendRequest(principal.getUserId(), userId);
    }

    @GetMapping("/requests/received")
    public List<ConnectionRequestDto> received(@AuthenticationPrincipal AppPrincipal principal) {
        return connectionService.receivedRequests(principal.getUserId());
    }

    @GetMapping("/requests/sent")
    public List<ConnectionRequestDto> sent(@AuthenticationPrincipal AppPrincipal principal) {
        return connectionService.sentRequests(principal.getUserId());
    }

    @PostMapping("/requests/{id}/accept")
    public ConnectionRequestDto accept(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        return connectionService.acceptRequest(principal.getUserId(), id);
    }

    @PostMapping("/requests/{id}/reject")
    public Map<String, String> reject(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        connectionService.rejectRequest(principal.getUserId(), id);
        return Map.of("message", "Connection request declined");
    }

    @DeleteMapping("/requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        connectionService.withdrawRequest(principal.getUserId(), id);
    }

    @GetMapping("/connections")
    public List<ConnectionUserDto> connections(@AuthenticationPrincipal AppPrincipal principal) {
        return connectionService.connectionsOf(principal.getUserId());
    }

    @DeleteMapping("/connections/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long userId, @AuthenticationPrincipal AppPrincipal principal) {
        connectionService.removeConnection(principal.getUserId(), userId);
    }

    @GetMapping("/connections/count")
    public Map<String, Long> count(@AuthenticationPrincipal AppPrincipal principal) {
        return Map.of("count", connectionService.connectionCount(principal.getUserId()));
    }
}
