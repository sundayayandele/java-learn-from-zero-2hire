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
        //>>
        String s = input.strip();
        if (s.startsWith("₦")) {
            s = s.substring(1);
        } else if (s.startsWith("NGN ")) {
            s = s.substring(4);
        }
        if (!s.matches("(\\d{1,3}(,\\d{3})*|\\d+)(\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("Invalid amount: " + input);
        }
        String[] parts = s.replace(",", "").split("\\.");
        long naira = Long.parseLong(parts[0]);
        long kobo = parts.length == 2 ? Long.parseLong((parts[1] + "0").substring(0, 2)) : 0;
        return Math.addExact(Math.multiplyExact(naira, 100), kobo);
        //<<
    }
}
