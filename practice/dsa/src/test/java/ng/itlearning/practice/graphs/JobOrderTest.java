package ng.itlearning.practice.graphs;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JobOrderTest {

    @Test
    void ordersJobs() {
        var deps = Map.of(
                "close-day", List.of("interest", "fees"),
                "interest", List.of("load-balances"),
                "fees", List.of("load-balances"),
                "load-balances", List.<String>of(),
                "audit", List.<String>of());
        assertThat(JobOrder.order(deps)).contains(List.of("audit", "load-balances", "fees", "interest", "close-day"));
    }

    @Test
    void detectsCycles() {
        assertThat(JobOrder.order(Map.of("a", List.of("b"), "b", List.of("c"), "c", List.of("a")))).isEmpty();
    }
}
