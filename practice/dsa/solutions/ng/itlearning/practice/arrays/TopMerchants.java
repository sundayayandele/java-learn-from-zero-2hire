package ng.itlearning.practice.arrays;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Week 4. Pattern: counting plus sorting (or a heap).
 * <p>
 * Return the {@code k} merchants that appear most often in a list of card payments, most frequent first;
 * ties are broken by merchant name ascending. If there are fewer than {@code k} merchants, return them all.
 */
public final class TopMerchants {

    private TopMerchants() {
    }

    public static List<String> topK(List<String> merchants, int k) {
        //>>
        Map<String, Integer> counts = new HashMap<>();
        merchants.forEach(m -> counts.merge(m, 1, Integer::sum));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(k)
                .map(Map.Entry::getKey)
                .toList();
        //<<
    }
}
