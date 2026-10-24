package ng.itlearning.practice.trees;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LevelOrderTest {

    @Test
    void levels() {
        TreeNode ceo = new TreeNode("Ada", 1);
        ceo.left = new TreeNode("Bayo", 2);
        ceo.right = new TreeNode("Chi", 3);
        ceo.left.right = new TreeNode("Dele", 4);
        ceo.right.left = new TreeNode("Efe", 5);
        assertThat(LevelOrder.levels(ceo)).containsExactly(List.of("Ada"), List.of("Bayo", "Chi"), List.of("Dele", "Efe"));
        assertThat(LevelOrder.levels(null)).isEmpty();
    }
}
