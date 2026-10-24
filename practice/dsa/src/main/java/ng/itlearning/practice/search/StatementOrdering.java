// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
package ng.itlearning.practice.search;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Week 14. Pattern: Comparator composition.
 * <p>
 * Sort transactions for an investigation report: largest amount first; for equal amounts (compare with
 * {@code compareTo}, so 10.0 equals 10.00), earliest time first; then reference ascending. Return a new list;
 * don't modify the input.
 */
public final class StatementOrdering {

    public record Txn(String reference, BigDecimal amount, Instant at) {
    }

    private StatementOrdering() {
    }

    public static List<Txn> sorted(List<Txn> txns) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
