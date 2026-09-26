package com.secureportal.hackathon;

import java.util.Arrays;
import java.util.Optional;

/** A listing of someone else's event, or an event run on the platform with its own teams, submissions and judging. */
public enum HackathonKind {
    EXTERNAL, HOSTED;

    public static Optional<HackathonKind> parse(String value) {
        return value == null ? Optional.empty() : Arrays.stream(values()).filter(k -> k.name().equalsIgnoreCase(value.strip())).findFirst();
    }
}
