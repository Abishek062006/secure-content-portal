package com.secureportal.infra;

import java.io.Serializable;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class InMemoryTtlCache implements TtlCache {

    private record Entry(Object value, long expiresAtMillis) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Serializable> T get(String key, Duration ttl, Supplier<T> loader) {
        if (ttl.isZero() || ttl.isNegative()) {
            return loader.get();
        }
        long now = System.currentTimeMillis();
        Entry hit = entries.get(key);
        if (hit != null && hit.expiresAtMillis() > now) {
            return (T) hit.value();
        }
        T value = loader.get();
        entries.put(key, new Entry(value, now + ttl.toMillis()));
        return value;
    }
}
