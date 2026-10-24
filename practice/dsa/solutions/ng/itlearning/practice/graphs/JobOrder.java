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
        //>>
        Map<String, Integer> remaining = new TreeMap<>();
        Map<String, List<String>> dependants = new HashMap<>();
        dependsOn.forEach((job, deps) -> {
            remaining.put(job, deps.size());
            deps.forEach(d -> dependants.computeIfAbsent(d, k -> new ArrayList<>()).add(job));
        });
        java.util.PriorityQueue<String> ready = new java.util.PriorityQueue<>();
        remaining.forEach((job, n) -> {
            if (n == 0) {
                ready.add(job);
            }
        });
        List<String> out = new ArrayList<>();
        while (!ready.isEmpty()) {
            String job = ready.poll();
            out.add(job);
            for (String next : dependants.getOrDefault(job, List.of())) {
                if (remaining.merge(next, -1, Integer::sum) == 0) {
                    ready.add(next);
                }
            }
        }
        return out.size() == dependsOn.size() ? Optional.of(out) : Optional.empty();
        //<<
    }
}
