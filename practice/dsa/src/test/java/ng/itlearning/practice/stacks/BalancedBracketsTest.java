package ng.itlearning.practice.stacks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BalancedBracketsTest {

    @Test
    void balanced() {
        assertThat(BalancedBrackets.isBalanced("(amount > [limit]) && {channel}")).isTrue();
        assertThat(BalancedBrackets.isBalanced("")).isTrue();
        assertThat(BalancedBrackets.isBalanced("{[()()]}")).isTrue();
    }

    @Test
    void unbalanced() {
        assertThat(BalancedBrackets.isBalanced("(]")).isFalse();
        assertThat(BalancedBrackets.isBalanced("((")).isFalse();
        assertThat(BalancedBrackets.isBalanced("())")).isFalse();
        assertThat(BalancedBrackets.isBalanced("{[}]")).isFalse();
    }
}
