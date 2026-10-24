package ng.itlearning.practice.graphs;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FewestHopsTest {

    static final Map<String, List<String>> GRAPH = Map.of(
            "A", List.of("B", "C"),
            "B", List.of("D"),
            "C", List.of("D", "E"),
            "D", List.of("F"),
            "E", List.of("F"),
            "F", List.of("A"));

    @Test
    void shortestPaths() {
        assertThat(FewestHops.hops(GRAPH, "A", "F")).isEqualTo(3);
        assertThat(FewestHops.hops(GRAPH, "E", "D")).isEqualTo(4);
        assertThat(FewestHops.hops(GRAPH, "A", "A")).isZero();
    }

    @Test
    void unreachable() {
        assertThat(FewestHops.hops(GRAPH, "A", "Z")).isEqualTo(-1);
        assertThat(FewestHops.hops(Map.of(), "X", "Y")).isEqualTo(-1);
    }
}
