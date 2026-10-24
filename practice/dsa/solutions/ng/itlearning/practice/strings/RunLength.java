package ng.itlearning.practice.strings;

/**
 * Week 9. Pattern: StringBuilder and counting runs.
 * <p>
 * Compress a status history such as "PPPSSF" to "P3S2F1". Return the original string if the compressed form is
 * not shorter. Use {@link StringBuilder}; string concatenation in a loop is O(n^2).
 */
public final class RunLength {

    private RunLength() {
    }

    public static String compress(String s) {
        //>>
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            int j = i;
            while (j < s.length() && s.charAt(j) == c) {
                j++;
            }
            sb.append(c).append(j - i);
            i = j;
        }
        return sb.length() < s.length() ? sb.toString() : s;
        //<<
    }
}
