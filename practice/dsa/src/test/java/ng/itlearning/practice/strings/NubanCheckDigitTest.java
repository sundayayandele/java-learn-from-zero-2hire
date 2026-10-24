package ng.itlearning.practice.strings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class NubanCheckDigitTest {

    @Test
    void computesCheckDigits() {
        // 0*3+5*7+8*3 + 0*3+0*7+0*3+0*3+0*7+0*3+0*3+0*7+1*3 = 62 -> 10 - 2 = 8
        assertThat(NubanCheckDigit.checkDigit("058", "000000001")).isEqualTo(8);
        // 0+7+3 + 1*3+2*7+3*3+4*3+5*7+6*3+7*3+8*7+9*3 = 205 -> 10 - 5 = 5
        assertThat(NubanCheckDigit.checkDigit("011", "123456789")).isEqualTo(5);
        // sum multiple of 10 gives 0: 000 + 000000000 -> 0
        assertThat(NubanCheckDigit.checkDigit("000", "000000000")).isZero();
    }

    @Test
    void validatesAccountNumbers() {
        assertThat(NubanCheckDigit.isValid("011", "1234567895")).isTrue();
        assertThat(NubanCheckDigit.isValid("011", "1234567894")).isFalse();
    }

    @Test
    void rejectsMalformedInput() {
        assertThatIllegalArgumentException().isThrownBy(() -> NubanCheckDigit.checkDigit("58", "000000001"));
        assertThatIllegalArgumentException().isThrownBy(() -> NubanCheckDigit.isValid("058", "12345"));
    }
}
