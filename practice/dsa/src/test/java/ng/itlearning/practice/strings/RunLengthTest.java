package ng.itlearning.practice.strings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RunLengthTest {

    @Test
    void compresses() {
        assertThat(RunLength.compress("PPPPSSF")).isEqualTo("P4S2F1");
        assertThat(RunLength.compress("AAAAAAAAAAAAB")).isEqualTo("A12B1");
    }

    @Test
    void keepsOriginalWhenNotShorter() {
        assertThat(RunLength.compress("PSF")).isEqualTo("PSF");
        assertThat(RunLength.compress("PPSS")).isEqualTo("PPSS");
        assertThat(RunLength.compress("")).isEmpty();
    }
}
