package com.secureportal.interview;

import java.util.Arrays;
import java.util.Optional;

/** Technical questions probe the skills and projects the learner brings; HR questions are behavioural, drawn at random. */
public enum InterviewType {
    TECHNICAL, HR;

    public static Optional<InterviewType> parse(String value) {
        return value == null ? Optional.empty() : Arrays.stream(values()).filter(t -> t.name().equalsIgnoreCase(value.strip())).findFirst();
    }
}
