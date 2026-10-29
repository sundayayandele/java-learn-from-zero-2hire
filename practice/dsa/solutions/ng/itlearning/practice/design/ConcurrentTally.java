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

    //>>hide
    private final Map<String, LongAdder> totals = new ConcurrentHashMap<>();

    //<<
    public void record(String merchant, long amountKobo) {
        //>>
        totals.computeIfAbsent(merchant, m -> new LongAdder()).add(amountKobo);
        //<<
    }

    public long total(String merchant) {
        //>>
        LongAdder adder = totals.get(merchant);
        return adder == null ? 0 : adder.sum();
        //<<
    }
}
