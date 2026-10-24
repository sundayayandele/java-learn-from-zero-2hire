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

    //>>hide
    private static final int[] WEIGHTS = {3, 7, 3, 3, 7, 3, 3, 7, 3, 3, 7, 3};

    //<<
    private NubanCheckDigit() {
    }

    public static int checkDigit(String bankCode, String serial) {
        //>>
        if (!bankCode.matches("\\d{3}") || !serial.matches("\\d{9}")) {
            throw new IllegalArgumentException("bank code must be 3 digits and serial 9 digits");
        }
        String all = bankCode + serial;
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (all.charAt(i) - '0') * WEIGHTS[i];
        }
        int check = 10 - sum % 10;
        return check == 10 ? 0 : check;
        //<<
    }

    public static boolean isValid(String bankCode, String accountNumber) {
        //>>
        if (!accountNumber.matches("\\d{10}")) {
            throw new IllegalArgumentException("account number must be 10 digits");
        }
        return checkDigit(bankCode, accountNumber.substring(0, 9)) == accountNumber.charAt(9) - '0';
        //<<
    }
}
