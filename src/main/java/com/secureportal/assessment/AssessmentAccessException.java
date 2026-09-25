package com.secureportal.assessment;

/** The learner may not do this right now: it's locked, or they're out of attempts. */
public class AssessmentAccessException extends RuntimeException {

    public AssessmentAccessException(String message) {
        super(message);
    }
}
