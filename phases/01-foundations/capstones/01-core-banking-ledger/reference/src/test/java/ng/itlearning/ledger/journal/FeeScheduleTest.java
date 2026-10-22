package ng.itlearning.ledger.journal;

import ng.itlearning.ledger.config.LedgerProperties.FeeTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeeScheduleTest {

    private final FeeSchedule fees = new FeeSchedule(List.of(
            new FeeTier(null, new BigDecimal("53.75")),                       // order in config must not matter
            new FeeTier(new BigDecimal("5000.00"), new BigDecimal("10.75")),
            new FeeTier(new BigDecimal("50000.00"), new BigDecimal("26.88"))));

    @ParameterizedTest
    @CsvSource({
            "0.01, 10.75",
            "5000.00, 10.75",
            "5000.01, 26.88",
            "50000.00, 26.88",
            "50000.01, 53.75",
            "10000000.00, 53.75"
    })
    void tierBoundaries(String amount, String expectedFee) {
        assertThat(fees.feeFor(new BigDecimal(amount))).isEqualByComparingTo(expectedFee);
    }

    @Test
    void requiresExactlyOneOpenEndedTier() {
        assertThatThrownBy(() -> new FeeSchedule(List.of(new FeeTier(BigDecimal.TEN, BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
