package ng.itlearning.practice.lists;

/**
 * Week 16. Pattern: dummy head and splicing.
 * <p>
 * Merge two sorted linked lists into one sorted list by relinking the existing nodes (no new nodes except an
 * optional dummy head).
 */
public final class MergeSortedLists {

    private MergeSortedLists() {
    }

    public static Node merge(Node a, Node b) {
        //>>
        Node dummy = new Node(0);
        Node tail = dummy;
        while (a != null && b != null) {
            if (a.value <= b.value) {
                tail.next = a;
                a = a.next;
            } else {
                tail.next = b;
                b = b.next;
            }
            tail = tail.next;
        }
        tail.next = a != null ? a : b;
        return dummy.next;
        //<<
    }
}
