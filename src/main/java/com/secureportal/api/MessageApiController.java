package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.AdminNotALearnerException;
import com.secureportal.messaging.ConversationDto;
import com.secureportal.messaging.MessageDto;
import com.secureportal.messaging.MessagingService;
import com.secureportal.messaging.SendMessageRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Messages between connected members. Rules and scoping live in {@link MessagingService}. */
@RestController
@RequestMapping("/api/messages")
public class MessageApiController {

    private static final int MAX_PAGE_SIZE = 100;

    private final MessagingService messagingService;

    public MessageApiController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @GetMapping("/conversations")
    public List<ConversationDto> conversations(@AuthenticationPrincipal AppPrincipal principal) {
        return messagingService.conversations(principal.getUserId());
    }

    @GetMapping("/conversations/{id}")
    public Page<MessageDto> history(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "30") int size, @AuthenticationPrincipal AppPrincipal principal) {
        return messagingService.history(principal.getUserId(), id, PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size))));
    }

    @PostMapping("/to/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageDto send(@PathVariable Long userId, @Valid @RequestBody SendMessageRequest request,
                           @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
        return messagingService.send(principal.getUserId(), userId, request.content());
    }

    @PostMapping("/conversations/{id}/read")
    public Map<String, Integer> markRead(@PathVariable Long id, @AuthenticationPrincipal AppPrincipal principal) {
        return Map.of("markedRead", messagingService.markRead(principal.getUserId(), id));
    }

    @GetMapping("/conversations/with/{userId}")
    public ConversationDto open(@PathVariable Long userId, @AuthenticationPrincipal AppPrincipal principal) {
        return messagingService.open(principal.getUserId(), userId);
    }
}
