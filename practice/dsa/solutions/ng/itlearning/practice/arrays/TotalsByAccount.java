package ng.itlearning.practice.arrays;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Week 3. Pattern: grouping with a map.
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
        //>>
        Map<String, Long> sums = new TreeMap<>();
        for (Posting p : postings) {
            sums.merge(p.account(), p.amountKobo(), Long::sum);
        }
        LinkedHashMap<String, Long> ordered = new LinkedHashMap<>();
        sums.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> ordered.put(e.getKey(), e.getValue()));
        return ordered;
        //<<
    }
}
