package ng.itlearning.minibank;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** Tracks how much each wallet has sent today. */
public class DailyLimitTracker {

    private final BigDecimal limit;
    private final Map<String, BigDecimal> usedToday = new HashMap<>();

    public DailyLimitTracker(BigDecimal limit) {
        this.limit = limit;
    }

    public boolean tryUse(String walletId, BigDecimal amount) {
        BigDecimal used = usedToday.getOrDefault(walletId, BigDecimal.ZERO);
        if (used.add(amount).compareTo(limit) > 0) {
            return false;
        }
        usedToday.put(walletId, used.add(amount));
        return true;
    }

    public BigDecimal used(String walletId) {
        return usedToday.getOrDefault(walletId, BigDecimal.ZERO);
    }
}
