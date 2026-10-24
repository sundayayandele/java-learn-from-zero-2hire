package ng.itlearning.practice.trees;

import org.junit.jupiter.api.Test;

import static ng.itlearning.practice.trees.TreeNode.of;
import static org.assertj.core.api.Assertions.assertThat;

class ValidSearchTreeTest {

    @Test
    void valid() {
        assertThat(ValidSearchTree.isValid(of(5, of(3, of(1, null, null), of(4, null, null)), of(8, null, of(9, null, null))))).isTrue();
        assertThat(ValidSearchTree.isValid(null)).isTrue();
        assertThat(ValidSearchTree.isValid(of(Integer.MAX_VALUE, of(Integer.MIN_VALUE, null, null), null))).isTrue();
    }

    @Test
    void invalidDeepViolationAndDuplicates() {
        assertThat(ValidSearchTree.isValid(of(5, of(3, null, of(6, null, null)), of(8, null, null)))).isFalse();
        assertThat(ValidSearchTree.isValid(of(5, of(5, null, null), null))).isFalse();
    }
}
