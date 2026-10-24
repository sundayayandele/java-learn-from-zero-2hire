package ng.itlearning.practice.windows;

/**
 * Week 7. Pattern: variable sliding window (positive values).
 * <p>
 * An AML rule flags the shortest run of consecutive deposits (all positive, kobo) whose sum is at least
 * {@code threshold}. Return its length, or 0 if no run reaches the threshold. O(n).
 */
public final class ShortestRunToTarget {

    private ShortestRunToTarget() {
    }

    public static int shortest(long[] deposits, long threshold) {
        //>>
        int best = Integer.MAX_VALUE;
        long sum = 0;
        int start = 0;
        for (int end = 0; end < deposits.length; end++) {
            sum += deposits[end];
            while (sum >= threshold) {
                best = Math.min(best, end - start + 1);
                sum -= deposits[start++];
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
        //<<
    }
}
