package ng.itlearning.practice.arrays;

/**
 * Week 4. Pattern: frequency counting.
 * <p>
 * Two customer-entered names are treated as the same set of letters if one is an anagram of the other,
 * ignoring case, spaces and hyphens ("Ade-Ola" and "Aloe Ad"). Return whether they are.
 */
public final class SameCharacters {

    private SameCharacters() {
    }

    public static boolean isAnagram(String a, String b) {
        //>>
        String x = normalise(a);
        String y = normalise(b);
        if (x.length() != y.length()) {
            return false;
        }
        int[] counts = new int[Character.MAX_VALUE + 1];
        for (int i = 0; i < x.length(); i++) {
            counts[x.charAt(i)]++;
            counts[y.charAt(i)]--;
        }
        for (int c : counts) {
            if (c != 0) {
                return false;
            }
        }
        return true;
        //<<
    }
    //>>hide

    private static String normalise(String s) {
        return s.toLowerCase().replace(" ", "").replace("-", "");
    }
    //<<
}
