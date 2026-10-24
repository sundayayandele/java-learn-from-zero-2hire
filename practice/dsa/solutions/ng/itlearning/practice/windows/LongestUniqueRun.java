package ng.itlearning.practice.windows;

import java.util.HashMap;
import java.util.Map;

/**
 * Week 6. Pattern: variable sliding window with a map.
 * <p>
 * Return the length of the longest substring without repeating characters. O(n).
 */
public final class LongestUniqueRun {

    private LongestUniqueRun() {
    }

    public static int length(String s) {
        //>>
        Map<Character, Integer> last = new HashMap<>();
        int start = 0;
        int best = 0;
        for (int end = 0; end < s.length(); end++) {
            Integer seen = last.put(s.charAt(end), end);
            if (seen != null && seen >= start) {
                start = seen + 1;
            }
            best = Math.max(best, end - start + 1);
        }
        return best;
        //<<
    }
}
