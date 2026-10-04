package com.secureportal.interview;

import java.util.Locale;
import java.util.Optional;

/** How the learner gave an answer. */
public enum AnswerMode {
    TYPED, VOICE;

    public static Optional<AnswerMode> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
