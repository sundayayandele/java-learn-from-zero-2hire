// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
package ng.itlearning.practice.windows;

import java.util.ArrayList;
import java.util.List;

/**
 * Week 5. Pattern: two pointers.
 * <p>
 * Two statement extracts are each sorted by booking time (epoch seconds). Merge them into one sorted list in
 * O(n + m). When times are equal, the entry from {@code a} comes first (a stable merge).
 */
public final class MergeSortedStatements {

    public record Entry(long bookedAt, String reference) {
    }

    private MergeSortedStatements() {
    }

    public static List<Entry> merge(List<Entry> a, List<Entry> b) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
