package ng.itlearning.cli;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterestTest {

    @Test
    void oneYearAtEighteenPercent() {
        assertThat(Interest.simple(new BigDecimal("250000.00"), new BigDecimal("18"), 12))
                .isEqualByComparingTo("45000.00");
    }

    @Test
    void roundsHalfEvenToKobo() {
        assertThat(Interest.simple(new BigDecimal("100.00"), new BigDecimal("1"), 1)).isEqualByComparingTo("0.08");
    }

    @Test
    void rejectsNegativeInputs() {
        assertThatThrownBy(() -> Interest.simple(new BigDecimal("-1"), BigDecimal.ONE, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
