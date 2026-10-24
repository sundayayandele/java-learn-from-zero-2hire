package ng.itlearning.practice.search;

import ng.itlearning.practice.search.StatementOrdering.Txn;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatementOrderingTest {

    static Txn t(String ref, String amount, String at) {
        return new Txn(ref, new BigDecimal(amount), Instant.parse(at));
    }

    @Test
    void ordersByAmountThenTimeThenReference() {
        List<Txn> input = new ArrayList<>(List.of(
                t("C", "10.00", "2026-01-01T10:00:00Z"),
                t("A", "500.00", "2026-01-01T09:00:00Z"),
                t("B", "10.0", "2026-01-01T09:00:00Z"),
                t("D", "10.00", "2026-01-01T09:00:00Z")));
        assertThat(StatementOrdering.sorted(input)).extracting(Txn::reference).containsExactly("A", "B", "D", "C");
        assertThat(input).extracting(Txn::reference).containsExactly("C", "A", "B", "D");
    }
}
