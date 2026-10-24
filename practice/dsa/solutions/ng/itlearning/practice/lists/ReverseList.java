package ng.itlearning.practice.lists;

/**
 * Week 14. Pattern: pointer manipulation.
 * <p>
 * Reverse a singly linked list in place and return the new head. O(n) time, O(1) extra space.
 */
public final class ReverseList {

    private ReverseList() {
    }

    public static Node reverse(Node head) {
        //>>
        Node prev = null;
        Node current = head;
        while (current != null) {
            Node next = current.next;
            current.next = prev;
            prev = current;
            current = next;
        }
        return prev;
        //<<
    }
}
