package com.secureportal.assessment;

/** The attempt is already submitted or has run out of time. */
public class AttemptClosedException extends RuntimeException {

    public AttemptClosedException(String message) {
        super(message);
    }
}
