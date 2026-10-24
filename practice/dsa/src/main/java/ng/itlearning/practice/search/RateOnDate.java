// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
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
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
