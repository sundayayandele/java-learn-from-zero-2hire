package ng.itlearning.practice.design;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Week 22. Pattern: rate limiting with an injected clock.
 * <p>
 * A token bucket for an API key: it holds at most {@code capacity} tokens, starts full, and refills continuously
 * at {@code capacity} tokens per {@code period}. {@link #tryAcquire()} takes one token if available. Never call
 * {@code Instant.now()} directly: use the {@link Clock} so tests can move time. Must be thread-safe.
 */
public final class TokenBucket {

    //>>hide
    private final Clock clock;
    private final int capacity;
    private final double tokensPerNano;
    private double tokens;
    private Instant last;

    //<<
    public TokenBucket(int capacity, Duration period, Clock clock) {
        //>>
        this.clock = clock;
        this.capacity = capacity;
        this.tokensPerNano = capacity / (double) period.toNanos();
        this.tokens = capacity;
        this.last = clock.instant();
        //<<
    }

    public synchronized boolean tryAcquire() {
        //>>
        Instant now = clock.instant();
        tokens = Math.min(capacity, tokens + Duration.between(last, now).toNanos() * tokensPerNano);
        last = now;
        if (tokens >= 1) {
            tokens -= 1;
            return true;
        }
        return false;
        //<<
    }
}
