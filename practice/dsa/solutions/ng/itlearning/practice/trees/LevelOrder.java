package ng.itlearning.practice.trees;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Week 18. Pattern: breadth-first search with a queue.
 * <p>
 * Return the names in an org chart level by level, left to right.
 */
public final class LevelOrder {

    private LevelOrder() {
    }

    public static List<List<String>> levels(TreeNode root) {
        //>>
        List<List<String>> out = new ArrayList<>();
        if (root == null) {
            return out;
        }
        Deque<TreeNode> queue = new ArrayDeque<>();
        queue.offer(root);
        while (!queue.isEmpty()) {
            int size = queue.size();
            List<String> level = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                TreeNode n = queue.poll();
                level.add(n.name);
                if (n.left != null) {
                    queue.offer(n.left);
                }
                if (n.right != null) {
                    queue.offer(n.right);
                }
            }
            out.add(level);
        }
        return out;
        //<<
    }
}
