package ng.itlearning.practice.graphs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Week 18. Pattern: union-find (or BFS/DFS over an undirected graph).
 * <p>
 * Fraud analysts treat accounts that have sent money to each other, directly or through other accounts, as one
 * group. Given transfers as [from, to] pairs, return the number of groups among all accounts that appear.
 */
public final class LinkedAccountGroups {

    public record Transfer(String from, String to) {
    }

    private LinkedAccountGroups() {
    }

    public static int groups(List<Transfer> transfers) {
        //>>
        Map<String, String> parent = new HashMap<>();
        for (Transfer t : transfers) {
            parent.putIfAbsent(t.from(), t.from());
            parent.putIfAbsent(t.to(), t.to());
            String a = find(parent, t.from());
            String b = find(parent, t.to());
            if (!a.equals(b)) {
                parent.put(a, b);
            }
        }
        int roots = 0;
        for (String account : parent.keySet()) {
            if (find(parent, account).equals(account)) {
                roots++;
            }
        }
        return roots;
        //<<
    }
    //>>hide

    private static String find(Map<String, String> parent, String x) {
        String root = x;
        while (!parent.get(root).equals(root)) {
            root = parent.get(root);
        }
        while (!parent.get(x).equals(root)) {                 // path compression
            String next = parent.get(x);
            parent.put(x, root);
            x = next;
        }
        return root;
    }
    //<<
}
