package ng.itlearning.practice.strings;

/**
 * Week 8. Pattern: digit processing.
 * <p>
 * Return whether a card number passes the Luhn check. Spaces are allowed between digits; any other non-digit,
 * or fewer than 13 or more than 19 digits, makes it invalid. Starting from the rightmost digit, double every
 * second digit, subtract 9 from doubled values above 9, sum everything; valid if the sum is a multiple of 10.
 */
public final class Luhn {

    private Luhn() {
    }

    public static boolean isValid(String pan) {
        //>>
        String digits = pan.replace(" ", "");
        if (!digits.matches("\\d{13,19}")) {
            return false;
        }
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
        //<<
    }
}
