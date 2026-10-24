package ng.itlearning.practice.dp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WaysToPayTest {

    @Test
    void counts() {
        assertThat(WaysToPay.ways(new int[] {1, 2, 5}, 5)).isEqualTo(4);
        assertThat(WaysToPay.ways(new int[] {2}, 3)).isZero();
        assertThat(WaysToPay.ways(new int[] {10}, 0)).isEqualTo(1);
        assertThat(WaysToPay.ways(new int[] {5, 10, 20, 50, 100, 200, 500, 1000}, 1000)).isEqualTo(104_561L);
    }
}
