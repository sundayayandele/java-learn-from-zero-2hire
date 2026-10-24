package ng.itlearning.practice.windows;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BestKDayWindowTest {

    @Test
    void findsTheBestWindow() {
        assertThat(BestKDayWindow.maxTotal(new long[] {5, -2, 9, 1, -8, 7, 7}, 3)).isEqualTo(12);
        assertThat(BestKDayWindow.maxTotal(new long[] {-5, -2, -9}, 1)).isEqualTo(-2);
        assertThat(BestKDayWindow.maxTotal(new long[] {4, 4}, 2)).isEqualTo(8);
    }

    @Test
    void rejectsBadK() {
        assertThatIllegalArgumentException().isThrownBy(() -> BestKDayWindow.maxTotal(new long[] {1}, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> BestKDayWindow.maxTotal(new long[] {1}, 0));
    }
}
