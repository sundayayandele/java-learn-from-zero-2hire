package ng.itlearning.practice.arrays;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Week 5. Pattern: hash set with sequence starts.
 * <p>
 * Given the (unsorted, possibly repeated) dates on which a customer transacted, return the length of the
 * longest run of consecutive calendar days. Aim for O(n) rather than sorting.
 */
public final class LongestActiveStreak {

    private LongestActiveStreak() {
    }

    public static int longest(List<LocalDate> days) {
        //>>
        Set<LocalDate> set = new HashSet<>(days);
        int best = 0;
        for (LocalDate d : set) {
            if (set.contains(d.minusDays(1))) {
                continue;                          // not the start of a streak
            }
            int length = 1;
            while (set.contains(d.plusDays(length))) {
                length++;
            }
            best = Math.max(best, length);
        }
        return best;
        //<<
    }
}
