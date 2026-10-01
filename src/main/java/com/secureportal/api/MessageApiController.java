package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.messaging.ConversationDto;
import com.secureportal.messaging.MessageDto;
import com.secureportal.messaging.MessagingService;
import com.secureportal.messaging.SendMessageRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageApiController {

    private final MessagingService messagingService;

    public MessageApiController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @GetMapping("/conversations")
    public List<ConversationDto> getConversations(@AuthenticationPrincipal AppPrincipal principal) {
        return messagingService.getConversations(principal.getUserId());
    }

    @GetMapping("/conversations/{id}")
    public Page<MessageDto> getConversationHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @AuthenticationPrincipal AppPrincipal principal) {
        return messagingService.getConversationHistory(
                principal.getUserId(), id, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    @PostMapping("/to/{userId}")
    public ResponseEntity<MessageDto> sendMessage(
            @PathVariable Long userId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal AppPrincipal principal) {
        MessageDto dto = messagingService.sendMessage(principal.getUserId(), userId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<Map<String, Integer>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal AppPrincipal principal) {
        int markedRead = messagingService.markAsRead(principal.getUserId(), id);
        return ResponseEntity.ok(Map.of("markedRead", markedRead));
    }

    @GetMapping("/conversations/with/{userId}")
    public ConversationDto getOrCreateConversation(
            @PathVariable Long userId,
            @AuthenticationPrincipal AppPrincipal principal) {
        return messagingService.getOrCreateConversation(principal.getUserId(), userId);
    }
}
