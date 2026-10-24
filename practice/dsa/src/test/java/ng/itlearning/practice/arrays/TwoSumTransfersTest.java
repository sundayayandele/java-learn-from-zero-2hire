package ng.itlearning.practice.arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TwoSumTransfersTest {

    @Test
    void findsThePair() {
        assertThat(TwoSumTransfers.find(new long[] {500_00, 2_000_00, 1_250_00, 750_00}, 2_000_00)).containsExactly(2, 3);
    }

    @Test
    void returnsTheFirstPairByRightIndex() {
        assertThat(TwoSumTransfers.find(new long[] {1, 4, 3, 2, 3}, 6)).containsExactly(1, 3);
    }

    @Test
    void handlesDuplicateValues() {
        assertThat(TwoSumTransfers.find(new long[] {300, 300}, 600)).containsExactly(0, 1);
    }

    @Test
    void returnsEmptyWhenNoPairExists() {
        assertThat(TwoSumTransfers.find(new long[] {1, 2, 3}, 100)).isEmpty();
        assertThat(TwoSumTransfers.find(new long[0], 0)).isEmpty();
    }

    @Test
    void isFastOnLargeInput() {
        long[] amounts = new long[200_000];
        for (int i = 0; i < amounts.length; i++) {
            amounts[i] = i * 2L;
        }
        amounts[amounts.length - 1] = 1;
        assertThat(TwoSumTransfers.find(amounts, 3)).containsExactly(1, amounts.length - 1);
    }
}
