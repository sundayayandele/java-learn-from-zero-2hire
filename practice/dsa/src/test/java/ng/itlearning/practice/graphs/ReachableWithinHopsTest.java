package ng.itlearning.practice.graphs;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReachableWithinHopsTest {

    static final Map<String, List<String>> G = Map.of("A", List.of("B", "C"), "B", List.of("D"), "D", List.of("E", "A"), "C", List.of());

    @Test
    void boundedReach() {
        assertThat(ReachableWithinHops.reachable(G, "A", 1)).containsExactly("B", "C");
        assertThat(ReachableWithinHops.reachable(G, "A", 2)).containsExactly("B", "C", "D");
        assertThat(ReachableWithinHops.reachable(G, "A", 10)).containsExactly("B", "C", "D", "E");
        assertThat(ReachableWithinHops.reachable(G, "A", 0)).isEmpty();
    }
}
