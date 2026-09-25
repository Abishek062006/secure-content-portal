package com.secureportal.assessment;

import java.util.UUID;

public class AssessmentNotFoundException extends RuntimeException {

    public AssessmentNotFoundException(UUID id) {
        super("An assessment was not found: " + id);
    }
}
