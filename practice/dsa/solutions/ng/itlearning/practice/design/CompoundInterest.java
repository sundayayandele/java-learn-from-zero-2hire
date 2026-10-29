package ng.itlearning.practice.design;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Week 25. Pattern: BigDecimal arithmetic with an explicit rounding policy.
 * <p>
 * Return the balance after {@code months} of monthly compounding at {@code annualRatePercent} (for example
 * "18.5"): each month multiply by (1 + rate / 100 / 12). Keep full precision during the calculation
 * (MathContext.DECIMAL64) and round once at the end to 2 decimal places with HALF_EVEN. Never use double.
 */
public final class CompoundInterest {

    private CompoundInterest() {
    }

    public static BigDecimal balance(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        //>>
        BigDecimal monthly = annualRatePercent.divide(BigDecimal.valueOf(1200), MathContext.DECIMAL64);
        BigDecimal factor = BigDecimal.ONE.add(monthly);
        BigDecimal result = principal;
        for (int i = 0; i < months; i++) {
            result = result.multiply(factor, MathContext.DECIMAL64);
        }
        return result.setScale(2, RoundingMode.HALF_EVEN);
        //<<
    }
}
