package com.secureportal.hackathon;

import java.util.Arrays;
import java.util.Optional;

public enum HackathonMode {
    ONLINE, OFFLINE, HYBRID;

    public static Optional<HackathonMode> parse(String value) {
        return value == null ? Optional.empty() : Arrays.stream(values()).filter(m -> m.name().equalsIgnoreCase(value.strip())).findFirst();
    }
}
