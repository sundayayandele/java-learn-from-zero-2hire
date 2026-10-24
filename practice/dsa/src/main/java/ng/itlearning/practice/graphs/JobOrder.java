// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
package ng.itlearning.practice.graphs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Week 19. Pattern: topological sort (Kahn's algorithm).
 * <p>
 * End-of-day batch jobs depend on each other: {@code dependsOn.get("interest")} lists jobs that must finish
 * before "interest". Every job appears as a key. Return an order that runs each job after its dependencies,
 * choosing the alphabetically smallest ready job whenever several are ready, or empty if there is a cycle.
 */
public final class JobOrder {

    private JobOrder() {
    }

    public static Optional<List<String>> order(Map<String, List<String>> dependsOn) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
