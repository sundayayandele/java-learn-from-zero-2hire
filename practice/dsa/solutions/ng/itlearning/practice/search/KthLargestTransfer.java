package ng.itlearning.practice.search;

import java.util.PriorityQueue;

/**
 * Week 13. Pattern: min-heap of size k.
 * <p>
 * Return the k-th largest amount (k = 1 is the largest) from a large array of transfers, in O(n log k) time
 * and O(k) extra space. Throw {@link IllegalArgumentException} if k is out of range.
 */
public final class KthLargestTransfer {

    private KthLargestTransfer() {
    }

    public static long kthLargest(long[] amounts, int k) {
        //>>
        if (k < 1 || k > amounts.length) {
            throw new IllegalArgumentException("bad k");
        }
        PriorityQueue<Long> heap = new PriorityQueue<>(k);
        for (long a : amounts) {
            if (heap.size() < k) {
                heap.offer(a);
            } else if (a > heap.peek()) {
                heap.poll();
                heap.offer(a);
            }
        }
        return heap.peek();
        //<<
    }
}
