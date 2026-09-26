package com.secureportal.interview;

import java.util.Arrays;

public enum QuestionCategory {
    TECHNICAL, SYSTEM_DESIGN, BEHAVIORAL, PROBLEM_SOLVING;

    /** The category the AI named, or TECHNICAL if it invented one. */
    public static QuestionCategory parseOrDefault(String value) {
        return value == null ? TECHNICAL
                : Arrays.stream(values()).filter(c -> c.name().equalsIgnoreCase(value.strip())).findFirst().orElse(TECHNICAL);
    }
}
