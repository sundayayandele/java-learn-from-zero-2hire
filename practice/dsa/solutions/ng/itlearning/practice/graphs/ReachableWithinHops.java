package ng.itlearning.practice.graphs;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Week 19. Pattern: bounded BFS.
 * <p>
 * For an AML investigation, return (sorted) every account that {@code start} could have moved money to within
 * at most {@code maxHops} transfers, excluding {@code start} itself.
 */
public final class ReachableWithinHops {

    private ReachableWithinHops() {
    }

    public static Set<String> reachable(Map<String, List<String>> sentTo, String start, int maxHops) {
        //>>
        Map<String, Integer> depth = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        depth.put(start, 0);
        queue.offer(start);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int d = depth.get(current);
            if (d == maxHops) {
                continue;
            }
            for (String next : sentTo.getOrDefault(current, List.of())) {
                if (!depth.containsKey(next)) {
                    depth.put(next, d + 1);
                    queue.offer(next);
                }
            }
        }
        Set<String> out = new TreeSet<>(depth.keySet());
        out.remove(start);
        return out;
        //<<
    }
}
