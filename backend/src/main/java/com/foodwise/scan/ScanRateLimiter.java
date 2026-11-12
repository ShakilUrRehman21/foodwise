package com.foodwise.scan;

import com.foodwise.common.ApiException;
import com.foodwise.config.FoodwiseProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter: at most N scans per kitchen per hour (SYSTEM_DESIGN 10.4, Roadmap 4.B9).
 * Per-instance; acceptable for the single-instance MVP deployment.
 */
@Component
public class ScanRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final int limit;
    private final Clock clock;
    private final Map<UUID, Deque<Instant>> hits = new ConcurrentHashMap<>();

    @Autowired
    public ScanRateLimiter(FoodwiseProperties props) {
        this(props.scan().maxScansPerHour(), Clock.systemUTC());
    }

    ScanRateLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    public void acquire(UUID kitchenId) {
        Instant now = clock.instant();
        Deque<Instant> q = hits.computeIfAbsent(kitchenId, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(WINDOW))) {
                q.pollFirst();
            }
            if (q.size() >= limit) {
                long wait = Duration.between(now, q.peekFirst().plus(WINDOW)).toMinutes() + 1;
                throw ApiException.rateLimited("Scan limit reached (" + limit + " per hour). Try again in about " + wait + " min.");
            }
            q.addLast(now);
        }
    }
}
