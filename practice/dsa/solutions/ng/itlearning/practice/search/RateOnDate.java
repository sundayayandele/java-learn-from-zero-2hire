package ng.itlearning.practice.search;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Week 12. Pattern: binary search (floor).
 * <p>
 * {@code publications} is sorted by date ascending with no duplicate dates. Return the rate in force on
 * {@code day}: the one with the latest date on or before it (reference rates aren't published at weekends),
 * or empty if {@code day} is before the first publication. O(log n).
 */
public final class RateOnDate {

    public record Publication(LocalDate date, String rate) {
    }

    private RateOnDate() {
    }

    public static Optional<String> rateOn(List<Publication> publications, LocalDate day) {
        //>>
        int lo = 0;
        int hi = publications.size() - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;                      // avoids int overflow
            if (!publications.get(mid).date().isAfter(day)) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found < 0 ? Optional.empty() : Optional.of(publications.get(found).rate());
        //<<
    }
}
