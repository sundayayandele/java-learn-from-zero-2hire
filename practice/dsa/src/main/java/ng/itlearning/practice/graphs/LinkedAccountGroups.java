// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
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
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
