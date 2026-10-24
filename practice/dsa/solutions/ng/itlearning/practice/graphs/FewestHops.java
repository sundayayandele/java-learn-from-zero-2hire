package ng.itlearning.practice.graphs;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Week 18. Pattern: breadth-first search for shortest paths in an unweighted graph.
 * <p>
 * Transfers are directed edges. Return the fewest transfers needed for money to move from {@code source} to
 * {@code target} (0 if they are the same account), or -1 if it can't.
 */
public final class FewestHops {

    private FewestHops() {
    }

    public static int hops(Map<String, List<String>> sentTo, String source, String target) {
        //>>
        if (source.equals(target)) {
            return 0;
        }
        Map<String, Integer> distance = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        distance.put(source, 0);
        queue.offer(source);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : sentTo.getOrDefault(current, List.of())) {
                if (!distance.containsKey(next)) {
                    int d = distance.get(current) + 1;
                    if (next.equals(target)) {
                        return d;
                    }
                    distance.put(next, d);
                    queue.offer(next);
                }
            }
        }
        return -1;
        //<<
    }
}
