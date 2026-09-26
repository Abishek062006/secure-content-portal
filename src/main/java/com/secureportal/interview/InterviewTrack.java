package com.secureportal.interview;

import java.util.Arrays;
import java.util.Optional;

public enum InterviewTrack {
    STUDENT, WORKING_PROFESSIONAL;

    public static Optional<InterviewTrack> parse(String value) {
        return value == null ? Optional.empty() : Arrays.stream(values()).filter(t -> t.name().equalsIgnoreCase(value.strip())).findFirst();
    }
}
