package ng.itlearning.practice.arrays;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Week 3. Pattern: hash set.
 * <p>
 * A settlement file must not contain the same transaction reference twice. Return the first reference that
 * appears for the second time (scanning in order), ignoring case and surrounding spaces, or empty if all are
 * unique. The returned value is the trimmed, upper-cased reference.
 */
public final class FirstDuplicateReference {

    private FirstDuplicateReference() {
    }

    public static Optional<String> find(List<String> references) {
        //>>
        Set<String> seen = new HashSet<>();
        for (String raw : references) {
            String ref = raw.trim().toUpperCase();
            if (!seen.add(ref)) {
                return Optional.of(ref);
            }
        }
        return Optional.empty();
        //<<
    }
}
