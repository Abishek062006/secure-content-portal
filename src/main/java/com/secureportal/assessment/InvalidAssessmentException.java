package com.secureportal.assessment;

/** The assessment settings, or a request against an attempt, break a rule. */
public class InvalidAssessmentException extends RuntimeException {

    public InvalidAssessmentException(String message) {
        super(message);
    }
}
