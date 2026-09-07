package com.secureportal.stream;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the single-range forms browsers actually send for HTML5 video
 * seeking: {@code bytes=start-end} or the open-ended {@code bytes=start-}.
 * Suffix-length ranges and multi-range requests aren't needed for this use
 * case and aren't handled.
 */
public record RangeRequest(long start, Long end) {

    private static final Pattern PATTERN = Pattern.compile("bytes=(\\d+)-(\\d*)");

    public static Optional<RangeRequest> parse(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(header.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        long start = Long.parseLong(matcher.group(1));
        String endGroup = matcher.group(2);
        Long end = endGroup.isEmpty() ? null : Long.parseLong(endGroup);
        return Optional.of(new RangeRequest(start, end));
    }
}
