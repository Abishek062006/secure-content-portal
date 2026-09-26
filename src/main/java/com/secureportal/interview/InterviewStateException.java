package com.secureportal.interview;

/** The action doesn't fit where the interview is: answering twice, finishing early, or changing a finished interview. */
public class InterviewStateException extends RuntimeException {

    public InterviewStateException(String message) {
        super(message);
    }
}
