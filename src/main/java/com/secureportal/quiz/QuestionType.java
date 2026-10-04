package com.secureportal.quiz;

import java.util.Locale;
import java.util.Optional;

/** What kind of answer a question wants. Concept questions are the plain multiple-choice ones. */
public enum QuestionType {
    MULTIPLE_CHOICE,
    /** A code snippet with one blank the learner fills in. */
    FILL_CODE,
    /** A code snippet the learner says the output of. */
    PREDICT_OUTPUT;

    /** The coding types: these carry a snippet, and the admin chooses whether learners type or pick the answer. */
    public boolean isCode() {
        return this != MULTIPLE_CHOICE;
    }

    public static Optional<QuestionType> parse(String value) {
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
