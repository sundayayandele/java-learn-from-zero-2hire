package ng.itlearning.practice.trees;

/**
 * Week 17. Pattern: post-order recursion.
 * <p>
 * Two staff must get approval from their lowest common manager. Given the root of a binary org chart and two
 * nodes that are both in it, return their lowest common ancestor (a node may be an ancestor of itself).
 */
public final class CommonManager {

    private CommonManager() {
    }

    public static TreeNode lowestCommon(TreeNode root, TreeNode a, TreeNode b) {
        //>>
        if (root == null || root == a || root == b) {
            return root;
        }
        TreeNode left = lowestCommon(root.left, a, b);
        TreeNode right = lowestCommon(root.right, a, b);
        if (left != null && right != null) {
            return root;
        }
        return left != null ? left : right;
        //<<
    }
}
