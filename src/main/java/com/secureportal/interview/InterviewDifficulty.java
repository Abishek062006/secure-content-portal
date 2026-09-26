package com.secureportal.interview;

import java.util.Arrays;
import java.util.Optional;

public enum InterviewDifficulty {
    EASY, MEDIUM, HARD;

    public static Optional<InterviewDifficulty> parse(String value) {
        return value == null ? Optional.empty() : Arrays.stream(values()).filter(d -> d.name().equalsIgnoreCase(value.strip())).findFirst();
    }
}
