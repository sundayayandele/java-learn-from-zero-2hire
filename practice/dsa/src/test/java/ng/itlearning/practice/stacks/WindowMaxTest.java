package ng.itlearning.practice.stacks;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WindowMaxTest {

    @Test
    void example() {
        assertThat(WindowMax.maxima(new int[] {1, 3, -1, -3, 5, 3, 6, 7}, 3)).containsExactly(3, 3, 5, 5, 6, 7);
        assertThat(WindowMax.maxima(new int[] {4}, 1)).containsExactly(4);
    }

    @Test
    void matchesBruteForceOnRandomInput() {
        Random r = new Random(7);
        int[] a = r.ints(2_000, 0, 1_000).toArray();
        int k = 50;
        int[] got = WindowMax.maxima(a, k);
        for (int i = 0; i + k <= a.length; i++) {
            int m = Integer.MIN_VALUE;
            for (int j = i; j < i + k; j++) {
                m = Math.max(m, a[j]);
            }
            assertThat(got[i]).isEqualTo(m);
        }
    }

    @Test
    void badK() {
        assertThatIllegalArgumentException().isThrownBy(() -> WindowMax.maxima(new int[] {1, 2}, 3));
    }
}
