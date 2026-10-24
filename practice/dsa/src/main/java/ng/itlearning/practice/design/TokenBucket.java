// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
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

    public TokenBucket(int capacity, Duration period, Clock clock) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public synchronized boolean tryAcquire() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
