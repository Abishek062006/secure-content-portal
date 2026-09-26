package com.secureportal.infra;

import java.time.Duration;

/** Counts requests per key in a fixed window. Shared through Redis when it's on, in this process otherwise. */
public interface RateLimiter {

    /** @param retryAfterSeconds when the window ends; only meaningful when {@code allowed} is false */
    record Result(boolean allowed, long retryAfterSeconds) {
    }

    Result hit(String key, int limit, Duration window);
}
