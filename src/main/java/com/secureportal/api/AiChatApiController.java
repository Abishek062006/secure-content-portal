package com.secureportal.api;

import com.secureportal.ai.AiChatService;
import com.secureportal.ai.AiChatService.ChatRequest;
import com.secureportal.ai.AiChatService.ChatResponse;
import com.secureportal.auth.AppPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/chat")
public class AiChatApiController {

    private final AiChatService chatService;

    public AiChatApiController(AiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@AuthenticationPrincipal AppPrincipal principal, @RequestBody ChatRequest request) {
        Long userId = principal != null ? principal.getUserId() : null;
        return chatService.chat(userId, request);
    }
}
