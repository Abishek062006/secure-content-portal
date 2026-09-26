package com.secureportal.interview;

/** An interview request that isn't acceptable (unknown track or difficulty, a stream that isn't a plain label, an answer too short or long). */
public class InvalidInterviewException extends RuntimeException {

    public InvalidInterviewException(String message) {
        super(message);
    }
}
