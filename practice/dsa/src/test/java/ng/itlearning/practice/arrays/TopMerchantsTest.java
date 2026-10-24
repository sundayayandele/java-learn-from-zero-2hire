package ng.itlearning.practice.arrays;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopMerchantsTest {

    @Test
    void mostFrequentFirstWithNameTieBreak() {
        var payments = List.of("Jumia", "Shoprite", "Uber", "Jumia", "Bolt", "Uber", "Jumia", "Bolt");
        assertThat(TopMerchants.topK(payments, 3)).containsExactly("Jumia", "Bolt", "Uber");
    }

    @Test
    void fewerMerchantsThanK() {
        assertThat(TopMerchants.topK(List.of("A", "B", "A"), 5)).containsExactly("A", "B");
    }

    @Test
    void zeroK() {
        assertThat(TopMerchants.topK(List.of("A"), 0)).isEmpty();
    }
}
