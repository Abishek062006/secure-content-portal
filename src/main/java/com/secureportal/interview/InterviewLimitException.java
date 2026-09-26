package com.secureportal.interview;

/** Too many interviews started in a day. Each one uses the AI, so the number is capped per learner. */
public class InterviewLimitException extends RuntimeException {

    public InterviewLimitException(int perDay) {
        super("You've reached today's limit of " + perDay + " mock interviews. Try again tomorrow.");
    }
}
