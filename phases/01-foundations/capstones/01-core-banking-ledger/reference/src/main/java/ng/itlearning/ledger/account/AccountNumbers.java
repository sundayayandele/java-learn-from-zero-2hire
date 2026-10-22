package ng.itlearning.ledger.account;

public final class AccountNumbers {

    public static final String CASH = "9000000001";
    public static final String FEE_INCOME = "9000000002";
    public static final String SUSPENSE = "9000000003";

    /** Shows only the last four digits, for logs and error messages. */
    public static String mask(String number) {
        if (number == null || number.length() < 4) {
            return "****";
        }
        return "******" + number.substring(number.length() - 4);
    }

    private AccountNumbers() { }
}
