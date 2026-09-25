package com.secureportal.assessment;

import java.util.UUID;

public class AttemptNotFoundException extends RuntimeException {

    public AttemptNotFoundException(UUID id) {
        super("An attempt was not found: " + id);
    }
}
