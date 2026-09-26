package com.secureportal.infra;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Single-process fixed-window counter: what runs when Redis is off. */
public class InMemoryRateLimiter implements RateLimiter {

    private record Window(long endsAtMillis, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastSweep = System.currentTimeMillis();

    @Override
    public Result hit(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        sweep(now);
        Window updated = windows.compute(key, (k, w) ->
                w == null || w.endsAtMillis() <= now ? new Window(now + window.toMillis(), 1) : new Window(w.endsAtMillis(), w.count() + 1));
        boolean allowed = updated.count() <= limit;
        return new Result(allowed, allowed ? 0 : Math.max(1, (updated.endsAtMillis() - now + 999) / 1000));
    }

    /** Drops finished windows now and then so an idle key doesn't sit in memory forever. */
    private void sweep(long now) {
        if (now - lastSweep < 60_000) {
            return;
        }
        lastSweep = now;
        for (Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator(); it.hasNext(); ) {
            if (it.next().getValue().endsAtMillis() <= now) {
                it.remove();
            }
        }
    }
}
