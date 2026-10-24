package ng.itlearning.practice.lists;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HasCycleTest {

    @Test
    void detectsCycles() {
        Node head = Node.of(1, 2, 3, 4, 5);
        assertThat(HasCycle.hasCycle(head)).isFalse();
        head.next.next.next.next.next = head.next;          // 5 -> 2
        assertThat(HasCycle.hasCycle(head)).isTrue();
        Node single = new Node(1);
        single.next = single;
        assertThat(HasCycle.hasCycle(single)).isTrue();
        assertThat(HasCycle.hasCycle(null)).isFalse();
    }
}
