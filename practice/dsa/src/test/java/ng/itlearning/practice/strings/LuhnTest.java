package ng.itlearning.practice.strings;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LuhnTest {

    @ParameterizedTest
    @CsvSource({
            "4242424242424242, true",
            "4242 4242 4242 4242, true",
            "5555555555554444, true",
            "4242424242424241, false",
            "4242-4242-4242-4242, false",
            "424242424242, false",
            "42424242424242424242, false"
    })
    void checks(String pan, boolean valid) {
        assertThat(Luhn.isValid(pan)).isEqualTo(valid);
    }
}
