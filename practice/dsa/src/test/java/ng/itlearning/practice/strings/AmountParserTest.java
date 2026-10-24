package ng.itlearning.practice.strings;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AmountParserTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "₦1,250.5|125050",
            "NGN 2000|200000",
            "0.01|1",
            "1,000,000.99|100000099",
            "  12  |1200",
            "999|99900"
    })
    void parses(String input, long kobo) {
        assertThat(AmountParser.toKobo(input)).isEqualTo(kobo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-5", "1,25,0", "1.234", "abc", "₦", "1,0000", "NGN-1", "1e3"})
    void rejects(String input) {
        assertThatIllegalArgumentException().isThrownBy(() -> AmountParser.toKobo(input));
    }
}
