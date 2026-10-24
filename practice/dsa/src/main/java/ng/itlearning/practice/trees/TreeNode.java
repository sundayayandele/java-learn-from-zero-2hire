// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
package ng.itlearning.practice.trees;

/** A binary tree node used by the tree problems. Provided; nothing to implement here. */
public final class TreeNode {
    public final String name;
    public final int value;
    public TreeNode left;
    public TreeNode right;

    public TreeNode(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public TreeNode(int value) {
        this(String.valueOf(value), value);
    }

    public static TreeNode of(int value, TreeNode left, TreeNode right) {
        TreeNode n = new TreeNode(value);
        n.left = left;
        n.right = right;
        return n;
    }
}
