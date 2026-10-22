package ng.itlearning.cli;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Interest {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    /** Simple interest, rounded to two decimal places with banker's rounding. */
    public static BigDecimal simple(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        if (principal.signum() < 0 || annualRatePercent.signum() < 0 || months < 0) {
            throw new IllegalArgumentException("Inputs must not be negative");
        }
        return principal.multiply(annualRatePercent)
                .multiply(BigDecimal.valueOf(months))
                .divide(HUNDRED.multiply(MONTHS_PER_YEAR), 2, RoundingMode.HALF_EVEN);
    }

    private Interest() { }
}
