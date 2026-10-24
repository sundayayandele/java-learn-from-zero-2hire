// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
package ng.itlearning.practice.strings;

/**
 * Week 8. Pattern: weighted checksum.
 * <p>
 * A 10-digit NUBAN account number is a 9-digit serial plus a check digit computed with the bank's 3-digit code
 * (CBN NUBAN standard). Multiply the 12 digits of bankCode + serial by the weights
 * 3,7,3,3,7,3,3,7,3,3,7,3, sum them, and the check digit is {@code 10 - (sum % 10)}, with 10 becoming 0.
 * Inputs that are not exactly 3 and 9 (or 10) digits throw {@link IllegalArgumentException}.
 */
public final class NubanCheckDigit {

    private NubanCheckDigit() {
    }

    public static int checkDigit(String bankCode, String serial) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public static boolean isValid(String bankCode, String accountNumber) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
