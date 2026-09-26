package com.secureportal.interview;

/** Too many interviews started in a day. Each one uses the AI, so the number is capped per learner. */
public class InterviewLimitException extends RuntimeException {

    public InterviewLimitException(int perDay) {
        this(perDay, "mock interviews");
    }

    public InterviewLimitException(int perDay, String what) {
        super("You've reached today's limit of " + perDay + " " + what + ". Try again tomorrow.");
    }
}
