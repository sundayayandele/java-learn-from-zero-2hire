package ng.itlearning.practice.lists;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MergeSortedListsTest {

    @Test
    void merges() {
        assertThat(Node.toList(MergeSortedLists.merge(Node.of(1, 2, 4), Node.of(1, 3, 4)))).containsExactly(1, 1, 2, 3, 4, 4);
        assertThat(Node.toList(MergeSortedLists.merge(null, Node.of(0)))).containsExactly(0);
        assertThat(MergeSortedLists.merge(null, null)).isNull();
    }

    @Test
    void reusesNodes() {
        Node a = Node.of(1);
        Node b = Node.of(2);
        Node merged = MergeSortedLists.merge(a, b);
        assertThat(merged).isSameAs(a);
        assertThat(merged.next).isSameAs(b);
    }
}
