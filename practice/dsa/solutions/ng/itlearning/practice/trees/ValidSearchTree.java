package ng.itlearning.practice.trees;

/**
 * Week 16. Pattern: recursion with bounds.
 * <p>
 * Return whether a binary tree is a valid binary search tree: every value in a node's left subtree is strictly
 * smaller and every value in its right subtree strictly larger. Checking only the children is not enough.
 */
public final class ValidSearchTree {

    private ValidSearchTree() {
    }

    public static boolean isValid(TreeNode root) {
        //>>
        return valid(root, Long.MIN_VALUE, Long.MAX_VALUE);
        //<<
    }
    //>>hide

    private static boolean valid(TreeNode n, long low, long high) {
        if (n == null) {
            return true;
        }
        if (n.value <= low || n.value >= high) {
            return false;
        }
        return valid(n.left, low, n.value) && valid(n.right, n.value, high);
    }
    //<<
}
