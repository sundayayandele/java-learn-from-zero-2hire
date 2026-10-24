package ng.itlearning.lab.flaky;

/** Formats kobo for SMS messages, for example 123450 as "₦1,234.50". */
public final class AmountFormatter {

    private AmountFormatter() {
    }

    public static String format(long kobo) {
        return String.format("₦%,.2f", kobo / 100.0);
    }
}
