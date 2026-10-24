package ng.itlearning.practice.dp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BestSingleTradeTest {

    @Test
    void profits() {
        assertThat(BestSingleTrade.maxProfit(new long[] {7, 1, 5, 3, 6, 4})).isEqualTo(5);
        assertThat(BestSingleTrade.maxProfit(new long[] {7, 6, 4, 3, 1})).isZero();
        assertThat(BestSingleTrade.maxProfit(new long[0])).isZero();
    }
}
