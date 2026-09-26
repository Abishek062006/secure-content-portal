package com.secureportal.infra;

import java.io.Serializable;
import java.time.Duration;
import java.util.function.Supplier;

/** A small read-through cache with an expiry, shared through Redis when it's on. A zero TTL bypasses the cache. */
public interface TtlCache {

    <T extends Serializable> T get(String key, Duration ttl, Supplier<T> loader);
}
