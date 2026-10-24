package ng.itlearning.practice.design;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SplitAmountTest {

    @Test
    void splitsExactly() {
        assertThat(SplitAmount.split(10_000, 3)).containsExactly(3334, 3333, 3333);
        assertThat(SplitAmount.split(2, 3)).containsExactly(1, 1, 0);
        assertThat(SplitAmount.split(0, 2)).containsExactly(0, 0);
        long[] big = SplitAmount.split(999_999_999_999L, 7);
        assertThat(Arrays.stream(big).sum()).isEqualTo(999_999_999_999L);
        assertThat(Arrays.stream(big).max().getAsLong() - Arrays.stream(big).min().getAsLong()).isLessThanOrEqualTo(1);
    }

    @Test
    void rejectsBadInput() {
        assertThatIllegalArgumentException().isThrownBy(() -> SplitAmount.split(100, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> SplitAmount.split(-1, 2));
    }
}
