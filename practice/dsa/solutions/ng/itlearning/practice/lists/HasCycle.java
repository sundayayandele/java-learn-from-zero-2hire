package ng.itlearning.practice.lists;

/**
 * Week 16. Pattern: fast and slow pointers (Floyd).
 * <p>
 * A corrupted approval chain may loop back on itself. Return whether the list has a cycle, using O(1) extra
 * space (no HashSet).
 */
public final class HasCycle {

    private HasCycle() {
    }

    public static boolean hasCycle(Node head) {
        //>>
        Node slow = head;
        Node fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) {
                return true;
            }
        }
        return false;
        //<<
    }
}
