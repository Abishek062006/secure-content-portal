package com.secureportal.ai;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One earlier turn of the conversation, as the browser keeps it. Only the member's and the assistant's own turns are accepted. */
public record ChatMessage(
        @NotNull(message = "Each earlier message needs a role.")
        @Pattern(regexp = "(?i)user|assistant", message = "An earlier message must be from the user or the assistant.")
        String role,
        @NotNull(message = "Each earlier message needs some text.")
        @Size(max = ChatRequest.MAX_HISTORY_CONTENT, message = "An earlier message is too long.")
        String content
) {
}
