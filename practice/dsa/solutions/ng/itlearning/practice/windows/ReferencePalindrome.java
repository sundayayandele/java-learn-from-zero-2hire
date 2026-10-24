package ng.itlearning.practice.windows;

/**
 * Week 5. Pattern: two pointers from both ends.
 * <p>
 * Return whether a string reads the same forwards and backwards when only letters and digits are considered,
 * ignoring case. Use O(1) extra space (don't build a cleaned copy).
 */
public final class ReferencePalindrome {

    private ReferencePalindrome() {
    }

    public static boolean isPalindrome(String s) {
        //>>
        int i = 0;
        int j = s.length() - 1;
        while (i < j) {
            if (!Character.isLetterOrDigit(s.charAt(i))) {
                i++;
            } else if (!Character.isLetterOrDigit(s.charAt(j))) {
                j--;
            } else if (Character.toLowerCase(s.charAt(i++)) != Character.toLowerCase(s.charAt(j--))) {
                return false;
            }
        }
        return true;
        //<<
    }
}
