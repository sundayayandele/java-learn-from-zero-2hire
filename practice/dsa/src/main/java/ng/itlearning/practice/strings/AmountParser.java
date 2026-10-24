// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
package ng.itlearning.practice.strings;

/**
 * Week 9. Pattern: careful parsing and validation.
 * <p>
 * Parse a naira amount typed by a user into kobo. Accept an optional leading "₦" or "NGN " prefix, comma
 * thousands separators in the right places, and at most two decimal places: "₦1,250.5" is 125050 kobo,
 * "NGN 2000" is 200000. Reject (with {@link IllegalArgumentException}) empty input, negative amounts, misplaced
 * commas such as "1,25,0", more than two decimals, and anything else. Never use double.
 */
public final class AmountParser {

    private AmountParser() {
    }

    public static long toKobo(String input) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
