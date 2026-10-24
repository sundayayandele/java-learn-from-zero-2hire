package ng.itlearning.practice.search;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class KthLargestTransferTest {

    @Test
    void examples() {
        assertThat(KthLargestTransfer.kthLargest(new long[] {3, 2, 1, 5, 6, 4}, 2)).isEqualTo(5);
        assertThat(KthLargestTransfer.kthLargest(new long[] {3, 2, 3, 1, 2, 4, 5, 5, 6}, 4)).isEqualTo(4);
    }

    @Test
    void matchesSorting() {
        long[] a = new Random(1).longs(50_000, 0, 1_000_000).toArray();
        long[] sorted = a.clone();
        Arrays.sort(sorted);
        assertThat(KthLargestTransfer.kthLargest(a, 100)).isEqualTo(sorted[sorted.length - 100]);
    }

    @Test
    void badK() {
        assertThatIllegalArgumentException().isThrownBy(() -> KthLargestTransfer.kthLargest(new long[] {1}, 2));
    }
}
