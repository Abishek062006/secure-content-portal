package com.secureportal.messaging;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Write a message first.")
        @Size(max = MessagingService.MAX_LENGTH, message = "Keep a message under 2000 characters.")
        String content
) {
}
