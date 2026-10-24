// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
package ng.itlearning.practice.design;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Week 24. Pattern: BigDecimal arithmetic with an explicit rounding policy.
 * <p>
 * Return the balance after {@code months} of monthly compounding at {@code annualRatePercent} (for example
 * "18.5"): each month multiply by (1 + rate / 100 / 12). Keep full precision during the calculation
 * (MathContext.DECIMAL64) and round once at the end to 2 decimal places with HALF_EVEN. Never use double.
 */
public final class CompoundInterest {

    private CompoundInterest() {
    }

    public static BigDecimal balance(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
