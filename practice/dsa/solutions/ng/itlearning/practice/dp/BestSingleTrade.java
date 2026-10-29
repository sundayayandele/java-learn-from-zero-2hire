package ng.itlearning.practice.dp;

/**
 * Week 22. Pattern: running minimum.
 * <p>
 * Given daily prices of a stock or currency, return the best profit from one buy followed by one later sell,
 * or 0 if no profitable trade exists. O(n).
 */
public final class BestSingleTrade {

    private BestSingleTrade() {
    }

    public static long maxProfit(long[] prices) {
        //>>
        long lowest = Long.MAX_VALUE;
        long best = 0;
        for (long p : prices) {
            lowest = Math.min(lowest, p);
            best = Math.max(best, p - lowest);
        }
        return best;
        //<<
    }
}
