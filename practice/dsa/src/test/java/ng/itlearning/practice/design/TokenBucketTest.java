package ng.itlearning.practice.design;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketTest {

    /** A clock the test moves by hand. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-01T09:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    @Test
    void allowsBurstThenRefills() {
        var clock = new MutableClock();
        var bucket = new TokenBucket(5, Duration.ofMinutes(1), clock);
        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryAcquire()).isTrue();
        }
        assertThat(bucket.tryAcquire()).isFalse();
        clock.advance(Duration.ofSeconds(12));           // 1 token per 12 s
        assertThat(bucket.tryAcquire()).isTrue();
        assertThat(bucket.tryAcquire()).isFalse();
        clock.advance(Duration.ofHours(1));              // never more than capacity
        int granted = 0;
        while (bucket.tryAcquire()) {
            granted++;
        }
        assertThat(granted).isEqualTo(5);
    }
}
