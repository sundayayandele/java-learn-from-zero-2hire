package ng.itlearning.practice.trees;

/**
 * Week 16. Pattern: recursion on trees.
 * <p>
 * Return the number of levels in a reporting hierarchy (an empty tree has depth 0).
 */
public final class TreeDepth {

    private TreeDepth() {
    }

    public static int depth(TreeNode root) {
        //>>
        return root == null ? 0 : 1 + Math.max(depth(root.left), depth(root.right));
        //<<
    }
}
