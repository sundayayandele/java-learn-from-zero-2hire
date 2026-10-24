package ng.itlearning.practice.stacks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DaysUntilHigherRateTest {

    @Test
    void examples() {
        assertThat(DaysUntilHigherRate.waits(new double[] {1530, 1528, 1535, 1535, 1520, 1540}))
                .containsExactly(2, 1, 3, 2, 1, 0);
        assertThat(DaysUntilHigherRate.waits(new double[] {3, 2, 1})).containsExactly(0, 0, 0);
        assertThat(DaysUntilHigherRate.waits(new double[0])).isEmpty();
    }
}
