package com.secureportal.hackathon;

import java.util.Arrays;
import java.util.Optional;

public enum HackathonStatus {
    UPCOMING, ACTIVE, COMPLETED;

    public static Optional<HackathonStatus> parse(String value) {
        return value == null ? Optional.empty() : Arrays.stream(values()).filter(s -> s.name().equalsIgnoreCase(value.strip())).findFirst();
    }
}
