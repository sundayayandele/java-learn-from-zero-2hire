package ng.itlearning.practice.windows;

/**
 * Week 7. Pattern: fixed-size sliding window.
 * <p>
 * Given daily transaction totals (kobo), return the largest total over any {@code k} consecutive days.
 * Throw {@link IllegalArgumentException} if {@code k} is not between 1 and the number of days. O(n).
 */
public final class BestKDayWindow {

    private BestKDayWindow() {
    }

    public static long maxTotal(long[] daily, int k) {
        //>>
        if (k < 1 || k > daily.length) {
            throw new IllegalArgumentException("k must be between 1 and " + daily.length);
        }
        long window = 0;
        for (int i = 0; i < k; i++) {
            window += daily[i];
        }
        long best = window;
        for (int i = k; i < daily.length; i++) {
            window += daily[i] - daily[i - k];
            best = Math.max(best, window);
        }
        return best;
        //<<
    }
}
