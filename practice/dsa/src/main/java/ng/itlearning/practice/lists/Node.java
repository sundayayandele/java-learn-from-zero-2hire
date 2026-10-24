// Generated from practice/dsa/solutions by scripts/practice_stubs.py: implement the TODOs, keep the signatures.
package ng.itlearning.practice.lists;

/** A singly linked list node used by the list problems. Provided; nothing to implement here. */
public final class Node {
    public int value;
    public Node next;

    public Node(int value) {
        this.value = value;
    }

    public static Node of(int... values) {
        Node dummy = new Node(0);
        Node tail = dummy;
        for (int v : values) {
            tail.next = new Node(v);
            tail = tail.next;
        }
        return dummy.next;
    }

    public static java.util.List<Integer> toList(Node head) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (Node n = head; n != null; n = n.next) {
            out.add(n.value);
        }
        return out;
    }
}
