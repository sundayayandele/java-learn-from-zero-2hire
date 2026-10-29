// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
package ng.itlearning.practice.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Week 13. Pattern: sort then sweep.
 * <p>
 * Maintenance windows are given as [start, end] minutes (start ≤ end), in any order. Merge overlapping or
 * touching windows ([1,3] and [3,5] become [1,5]) and return them sorted by start.
 */
public final class MergeWindows {

    public record Window(int start, int end) {
    }

    private MergeWindows() {
    }

    public static List<Window> merge(List<Window> windows) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
