package ng.itlearning.practice.design;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CompoundInterestTest {

    @Test
    void compoundsMonthly() {
        assertThat(CompoundInterest.balance(new BigDecimal("100000.00"), new BigDecimal("12"), 12))
                .isEqualTo(new BigDecimal("112682.50"));
        assertThat(CompoundInterest.balance(new BigDecimal("50000.00"), new BigDecimal("18.5"), 6))
                .isEqualTo(new BigDecimal("54806.96"));
    }

    @Test
    void zeroMonthsOrRateKeepsPrincipal() {
        assertThat(CompoundInterest.balance(new BigDecimal("2500.555"), new BigDecimal("10"), 0))
                .isEqualTo(new BigDecimal("2500.56"));
        assertThat(CompoundInterest.balance(new BigDecimal("1000.00"), BigDecimal.ZERO, 24))
                .isEqualTo(new BigDecimal("1000.00"));
    }
}
