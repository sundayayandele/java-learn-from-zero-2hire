// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
package ng.itlearning.practice.design;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Week 25. Pattern: thread-safe aggregation.
 * <p>
 * Many threads record card payments per merchant at once. Implement {@code record} and {@code total} so that no
 * update is ever lost, without a global {@code synchronized} lock (use ConcurrentHashMap with atomic operations,
 * or LongAdder).
 */
public final class ConcurrentTally {

    public void record(String merchant, long amountKobo) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public long total(String merchant) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
