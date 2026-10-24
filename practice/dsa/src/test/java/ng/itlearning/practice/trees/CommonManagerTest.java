package ng.itlearning.practice.trees;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommonManagerTest {

    @Test
    void findsTheLowestCommonManager() {
        TreeNode md = new TreeNode("MD", 1);
        TreeNode ops = new TreeNode("Ops", 2);
        TreeNode tech = new TreeNode("Tech", 3);
        TreeNode payments = new TreeNode("Payments", 4);
        TreeNode cards = new TreeNode("Cards", 5);
        TreeNode branch = new TreeNode("Branch", 6);
        md.left = ops;
        md.right = tech;
        tech.left = payments;
        tech.right = cards;
        ops.left = branch;
        assertThat(CommonManager.lowestCommon(md, payments, cards)).isSameAs(tech);
        assertThat(CommonManager.lowestCommon(md, branch, cards)).isSameAs(md);
        assertThat(CommonManager.lowestCommon(md, tech, cards)).isSameAs(tech);
    }
}
