package ng.itlearning.practice.windows;

import java.util.ArrayList;
import java.util.List;

/**
 * Week 6. Pattern: two pointers.
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
        //>>
        List<Entry> out = new ArrayList<>(a.size() + b.size());
        int i = 0;
        int j = 0;
        while (i < a.size() && j < b.size()) {
            out.add(a.get(i).bookedAt() <= b.get(j).bookedAt() ? a.get(i++) : b.get(j++));
        }
        out.addAll(a.subList(i, a.size()));
        out.addAll(b.subList(j, b.size()));
        return out;
        //<<
    }
}
