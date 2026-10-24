package ng.itlearning.practice.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Week 12. Pattern: sort then sweep.
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
        //>>
        List<Window> sorted = new ArrayList<>(windows);
        sorted.sort(Comparator.comparingInt(Window::start));
        List<Window> out = new ArrayList<>();
        for (Window w : sorted) {
            if (!out.isEmpty() && w.start() <= out.getLast().end()) {
                Window last = out.removeLast();
                out.add(new Window(last.start(), Math.max(last.end(), w.end())));
            } else {
                out.add(w);
            }
        }
        return out;
        //<<
    }
}
