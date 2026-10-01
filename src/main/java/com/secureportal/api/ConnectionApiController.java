package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.network.ConnectionRequestDto;
import com.secureportal.network.ConnectionService;
import com.secureportal.network.ConnectionUserDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/network")
public class ConnectionApiController {

    private final ConnectionService connectionService;

    public ConnectionApiController(ConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @GetMapping("/suggestions")
    public Page<ConnectionUserDto> suggestions(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AppPrincipal principal) {
        return connectionService.getSuggestions(principal.getUserId(), q, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @PostMapping("/requests/{userId}")
    public ResponseEntity<ConnectionRequestDto> sendRequest(
            @PathVariable Long userId,
            @AuthenticationPrincipal AppPrincipal principal) {
        ConnectionRequestDto request = connectionService.sendRequest(principal.getUserId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @GetMapping("/requests/received")
    public List<ConnectionRequestDto> getReceivedRequests(@AuthenticationPrincipal AppPrincipal principal) {
        return connectionService.getReceivedRequests(principal.getUserId());
    }

    @GetMapping("/requests/sent")
    public List<ConnectionRequestDto> getSentRequests(@AuthenticationPrincipal AppPrincipal principal) {
        return connectionService.getSentRequests(principal.getUserId());
    }

    @PostMapping("/requests/{id}/accept")
    public ConnectionRequestDto acceptRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal AppPrincipal principal) {
        return connectionService.acceptRequest(principal.getUserId(), id);
    }

    @PostMapping("/requests/{id}/reject")
    public ResponseEntity<Map<String, String>> rejectRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal AppPrincipal principal) {
        connectionService.rejectRequest(principal.getUserId(), id);
        return ResponseEntity.ok(Map.of("message", "Connection request rejected"));
    }

    @DeleteMapping("/requests/{id}")
    public ResponseEntity<Void> withdrawRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal AppPrincipal principal) {
        connectionService.withdrawRequest(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/connections")
    public List<ConnectionUserDto> getConnections(@AuthenticationPrincipal AppPrincipal principal) {
        return connectionService.getConnections(principal.getUserId());
    }

    @DeleteMapping("/connections/{userId}")
    public ResponseEntity<Void> removeConnection(
            @PathVariable Long userId,
            @AuthenticationPrincipal AppPrincipal principal) {
        connectionService.removeConnection(principal.getUserId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/connections/count")
    public Map<String, Long> getConnectionsCount(@AuthenticationPrincipal AppPrincipal principal) {
        return Map.of("count", connectionService.getAcceptedConnectionCount(principal.getUserId()));
    }
}
