package ng.itlearning.lab.flaky;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AmountFormatterTest {

    @Test
    void formatsNaira() {
        assertThat(AmountFormatter.format(123_450)).isEqualTo("₦1,234.50");
        assertThat(AmountFormatter.format(100_000_000_01L)).isEqualTo("₦100,000,000.01");
    }
}
