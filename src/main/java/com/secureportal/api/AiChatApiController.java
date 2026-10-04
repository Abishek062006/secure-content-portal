package com.secureportal.api;

import com.secureportal.ai.AiChatService;
import com.secureportal.ai.ChatRequest;
import com.secureportal.ai.ChatResponse;
import com.secureportal.auth.AppPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The AI assistant. Signed-in members only; limits and the rules for what reaches the model live in {@link AiChatService}. */
@RestController
@RequestMapping("/api/ai/chat")
public class AiChatApiController {

    private final AiChatService chatService;

    public AiChatApiController(AiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@AuthenticationPrincipal AppPrincipal principal, @Valid @RequestBody ChatRequest request) {
        return chatService.chat(principal.getUserId(), request);
    }
}
