package ng.itlearning.practice.trees;

import org.junit.jupiter.api.Test;

import static ng.itlearning.practice.trees.TreeNode.of;
import static org.assertj.core.api.Assertions.assertThat;

class TreeDepthTest {

    @Test
    void depths() {
        assertThat(TreeDepth.depth(null)).isZero();
        assertThat(TreeDepth.depth(new TreeNode(1))).isEqualTo(1);
        assertThat(TreeDepth.depth(of(1, of(2, of(4, null, null), null), of(3, null, null)))).isEqualTo(3);
    }
}
