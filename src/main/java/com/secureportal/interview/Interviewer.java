package com.secureportal.interview;

import java.util.Arrays;
import java.util.Optional;

/**
 * The people a learner can be interviewed by. Each has their own way of talking, which shapes how the AI words the questions; how they
 * look and sound is the browser's business.
 */
public enum Interviewer {
    PRIYA("Priya", "an HR lead who is warm and encouraging, puts people at ease and uses friendly, everyday language"),
    ARJUN("Arjun", "a senior engineer who is direct and curious, gets straight to the point and likes concrete examples"),
    SARAH("Sarah", "an engineering manager who is calm and structured, and frames questions around real situations at work"),
    DAVID("David", "a tech lead who is relaxed and upbeat, and talks like a teammate rather than an examiner"),
    MEI("Mei", "a talent partner who is crisp and professional, and asks clear, well-paced questions");

    private final String displayName;
    private final String style;

    Interviewer(String displayName, String style) {
        this.displayName = displayName;
        this.style = style;
    }

    public String displayName() {
        return displayName;
    }

    /** One line for the AI prompt: who is asking, and how they talk. */
    public String promptLine() {
        return "Interviewer: " + displayName + ", " + style + ". Word every question the way they would say it.";
    }

    public static Optional<Interviewer> parse(String value) {
        return value == null ? Optional.empty() : Arrays.stream(values()).filter(i -> i.name().equalsIgnoreCase(value.strip())).findFirst();
    }

    /** Who interviews when the learner hasn't picked anyone: an HR lead for an HR round, an engineer otherwise. */
    public static Interviewer defaultFor(InterviewType type) {
        return type == InterviewType.HR ? PRIYA : ARJUN;
    }
}
