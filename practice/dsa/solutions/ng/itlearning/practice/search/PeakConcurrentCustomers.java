package ng.itlearning.practice.search;

import java.util.Arrays;

/**
 * Week 14. Pattern: sweep line over sorted events.
 * <p>
 * A branch records each customer's arrival and departure minute ({@code arrivals[i]} ≤ {@code departures[i]}).
 * A customer leaving at minute t frees a teller for one arriving at t. Return the maximum number of customers
 * present at once, which is the number of tellers needed. O(n log n).
 */
public final class PeakConcurrentCustomers {

    private PeakConcurrentCustomers() {
    }

    public static int peak(int[] arrivals, int[] departures) {
        //>>
        int[] a = arrivals.clone();
        int[] d = departures.clone();
        Arrays.sort(a);
        Arrays.sort(d);
        int i = 0;
        int j = 0;
        int present = 0;
        int best = 0;
        while (i < a.length) {
            if (a[i] < d[j]) {
                present++;
                best = Math.max(best, present);
                i++;
            } else {
                present--;
                j++;
            }
        }
        return best;
        //<<
    }
}
