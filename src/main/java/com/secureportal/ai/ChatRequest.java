package com.secureportal.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A question for the assistant, with the conversation so far and optionally a text or code file (its text, not an image: the
 * assistant reads text). Every part is bounded, so one request can't carry an unbounded amount of text to the AI.
 */
public record ChatRequest(
        @NotBlank(message = "Write a question first.")
        @Size(max = ChatRequest.MAX_MESSAGE, message = "Keep your question under 2000 characters.")
        String message,
        @Size(max = ChatRequest.MAX_HISTORY, message = "The conversation is too long to send. Start a new chat.")
        List<@Valid ChatMessage> history,
        @Size(max = 120, message = "The file name is too long.")
        String attachmentName,
        @Size(max = ChatRequest.MAX_ATTACHMENT, message = "An attached file can hold up to 20,000 characters.")
        String attachmentText
) {
    public static final int MAX_MESSAGE = 2000;
    public static final int MAX_HISTORY = 12;
    public static final int MAX_HISTORY_CONTENT = 6000;
    public static final int MAX_ATTACHMENT = 20_000;
}
