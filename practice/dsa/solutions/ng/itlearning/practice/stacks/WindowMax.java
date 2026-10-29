package ng.itlearning.practice.stacks;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Week 12. Pattern: monotonic deque.
 * <p>
 * For a stream of per-minute transaction counts, return the maximum of every window of {@code k} consecutive
 * minutes (n - k + 1 values). O(n), not O(n·k). Throw {@link IllegalArgumentException} for k outside 1..n.
 */
public final class WindowMax {

    private WindowMax() {
    }

    public static int[] maxima(int[] counts, int k) {
        //>>
        if (k < 1 || k > counts.length) {
            throw new IllegalArgumentException("bad k");
        }
        int[] out = new int[counts.length - k + 1];
        Deque<Integer> dq = new ArrayDeque<>();              // indices, values decreasing
        for (int i = 0; i < counts.length; i++) {
            while (!dq.isEmpty() && dq.peekFirst() <= i - k) {
                dq.pollFirst();
            }
            while (!dq.isEmpty() && counts[dq.peekLast()] <= counts[i]) {
                dq.pollLast();
            }
            dq.offerLast(i);
            if (i >= k - 1) {
                out[i - k + 1] = counts[dq.peekFirst()];
            }
        }
        return out;
        //<<
    }
}
