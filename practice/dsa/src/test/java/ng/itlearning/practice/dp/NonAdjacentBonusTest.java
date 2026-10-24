package ng.itlearning.practice.dp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NonAdjacentBonusTest {

    @Test
    void totals() {
        assertThat(NonAdjacentBonus.best(new long[] {2, 7, 9, 3, 1})).isEqualTo(12);
        assertThat(NonAdjacentBonus.best(new long[] {5, 1, 1, 5})).isEqualTo(10);
        assertThat(NonAdjacentBonus.best(new long[0])).isZero();
    }
}
