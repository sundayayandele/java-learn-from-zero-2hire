// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
package ng.itlearning.practice.arrays;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Week 4. Pattern: grouping with a map.
 * <p>
 * Given postings (account number and a signed amount in kobo: credits positive, debits negative), return each
 * account's net total, ordered by total descending and, for equal totals, by account number ascending.
 */
public final class TotalsByAccount {

    public record Posting(String account, long amountKobo) {
    }

    private TotalsByAccount() {
    }

    public static LinkedHashMap<String, Long> totals(List<Posting> postings) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
