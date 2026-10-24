package ng.itlearning.practice.lists;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReverseListTest {

    @Test
    void reverses() {
        assertThat(Node.toList(ReverseList.reverse(Node.of(1, 2, 3, 4)))).containsExactly(4, 3, 2, 1);
        assertThat(Node.toList(ReverseList.reverse(Node.of(7)))).containsExactly(7);
        assertThat(ReverseList.reverse(null)).isNull();
    }
}
