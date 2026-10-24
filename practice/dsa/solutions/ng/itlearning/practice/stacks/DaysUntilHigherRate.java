package ng.itlearning.practice.stacks;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Week 10. Pattern: monotonic stack.
 * <p>
 * Given daily exchange rates, return for each day how many days you must wait for a strictly higher rate,
 * or 0 if it never comes. O(n).
 */
public final class DaysUntilHigherRate {

    private DaysUntilHigherRate() {
    }

    public static int[] waits(double[] rates) {
        //>>
        int[] out = new int[rates.length];
        Deque<Integer> stack = new ArrayDeque<>();           // indices with no higher rate found yet
        for (int i = 0; i < rates.length; i++) {
            while (!stack.isEmpty() && rates[stack.peek()] < rates[i]) {
                int j = stack.pop();
                out[j] = i - j;
            }
            stack.push(i);
        }
        return out;
        //<<
    }
}
