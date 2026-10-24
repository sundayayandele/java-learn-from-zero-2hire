package ng.itlearning.practice.arrays;

import ng.itlearning.practice.arrays.TotalsByAccount.Posting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TotalsByAccountTest {

    @Test
    void netsAndOrdersByTotalThenAccount() {
        var totals = TotalsByAccount.totals(List.of(
                new Posting("0003", 5_000), new Posting("0001", 10_000), new Posting("0002", 7_000),
                new Posting("0001", -3_000), new Posting("0003", 2_000), new Posting("0004", -100)));
        assertThat(totals).containsExactly(
                org.assertj.core.api.Assertions.entry("0001", 7_000L),
                org.assertj.core.api.Assertions.entry("0002", 7_000L),
                org.assertj.core.api.Assertions.entry("0003", 7_000L),
                org.assertj.core.api.Assertions.entry("0004", -100L));
    }

    @Test
    void emptyInputGivesEmptyMap() {
        assertThat(TotalsByAccount.totals(List.of())).isEmpty();
    }
}
