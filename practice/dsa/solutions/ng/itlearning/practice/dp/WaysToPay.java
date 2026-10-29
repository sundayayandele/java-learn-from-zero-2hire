package ng.itlearning.practice.dp;

/**
 * Week 21. Pattern: dynamic programming (unbounded combinations).
 * <p>
 * Count the distinct combinations of note denominations (unlimited of each) that make exactly {@code amount}.
 * Order doesn't matter: 5+10 and 10+5 are the same combination. Return the count as a long.
 */
public final class WaysToPay {

    private WaysToPay() {
    }

    public static long ways(int[] notes, int amount) {
        //>>
        long[] ways = new long[amount + 1];
        ways[0] = 1;
        for (int note : notes) {
            for (int a = note; a <= amount; a++) {
                ways[a] += ways[a - note];
            }
        }
        return ways[amount];
        //<<
    }
}
