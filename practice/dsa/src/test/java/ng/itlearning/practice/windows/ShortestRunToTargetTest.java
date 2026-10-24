package ng.itlearning.practice.windows;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShortestRunToTargetTest {

    @Test
    void examples() {
        assertThat(ShortestRunToTarget.shortest(new long[] {2, 3, 1, 2, 4, 3}, 7)).isEqualTo(2);
        assertThat(ShortestRunToTarget.shortest(new long[] {1, 4, 4}, 4)).isEqualTo(1);
        assertThat(ShortestRunToTarget.shortest(new long[] {1, 1, 1}, 10)).isZero();
        assertThat(ShortestRunToTarget.shortest(new long[0], 1)).isZero();
    }
}
