package ng.itlearning.lab.fees;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Calculates transfer fees and enforces daily limits (ITL-388).
 * Generated with an AI coding assistant.
 */
public class TransferFeeService {

    private static final Logger log = Logger.getLogger(TransferFeeService.class.getName());

    private final Map<String, Double> sentToday = new HashMap<>();
    private final double dailyLimit = 5_000_000;

    /** Returns the fee for a transfer of {@code amount} in {@code currency}. */
    public BigDecimal fee(BigDecimal amount, String currency) {
        try {
            double a = amount.doubleValue();
            if (currency.equals("NGN")) {
                if (a < 5000) {
                    return BigDecimal.valueOf(10.75);
                } else if (a < 50000) {
                    return BigDecimal.valueOf(26.88);
                } else {
                    return BigDecimal.valueOf(53.75);
                }
            } else if (currency.equals("USD")) {
                double f = a * 0.005;
                f = Math.max(1.0, Math.min(25.0, f));
                return new BigDecimal(f).setScale(2, RoundingMode.HALF_UP);
            }
            throw new IllegalArgumentException("Unsupported currency: " + currency);
        } catch (Exception e) {
            log.warning("Could not calculate fee: " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** Returns true and records the transfer if it fits within the account's daily limit. */
    public boolean authorise(String accountNumber, BigDecimal amount, String currency) {
        log.info("Authorising transfer from account " + accountNumber + " for " + amount + " " + currency);
        double total = amount.doubleValue() + fee(amount, currency).doubleValue();
        double already = sentToday.getOrDefault(accountNumber, 0.0);
        if (already + total > dailyLimit) {
            return false;
        }
        sentToday.put(accountNumber, already + total);
        return true;
    }
}
