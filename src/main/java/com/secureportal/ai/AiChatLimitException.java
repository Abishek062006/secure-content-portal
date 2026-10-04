package com.secureportal.ai;

/** A member has used today's allowance of questions for the assistant. Each one is an AI call, so the number is capped. */
public class AiChatLimitException extends RuntimeException {

    public AiChatLimitException(int perDay) {
        super("You've reached today's limit of " + perDay + " questions for the assistant. Try again tomorrow.");
    }
}
