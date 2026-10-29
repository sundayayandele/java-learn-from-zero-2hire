package ng.itlearning.practice.dp;

import java.util.Arrays;

/**
 * Week 21. Pattern: dynamic programming (minimum).
 * <p>
 * An ATM must dispense exactly {@code amount} with the fewest notes from the given denominations (unlimited of
 * each). Return that number, or -1 if it is impossible. Greedy choice fails for some denomination sets.
 */
public final class FewestNotes {

    private FewestNotes() {
    }

    public static int fewest(int[] notes, int amount) {
        //>>
        int[] best = new int[amount + 1];
        Arrays.fill(best, Integer.MAX_VALUE);
        best[0] = 0;
        for (int a = 1; a <= amount; a++) {
            for (int note : notes) {
                if (note <= a && best[a - note] != Integer.MAX_VALUE) {
                    best[a] = Math.min(best[a], best[a - note] + 1);
                }
            }
        }
        return best[amount] == Integer.MAX_VALUE ? -1 : best[amount];
        //<<
    }
}
