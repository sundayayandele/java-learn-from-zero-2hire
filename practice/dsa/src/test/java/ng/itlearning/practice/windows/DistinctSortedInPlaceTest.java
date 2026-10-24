package ng.itlearning.practice.windows;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DistinctSortedInPlaceTest {

    @Test
    void compactsInPlace() {
        int[] ids = {1, 1, 2, 3, 3, 3, 7};
        int n = DistinctSortedInPlace.compact(ids);
        assertThat(n).isEqualTo(4);
        assertThat(Arrays.copyOf(ids, n)).containsExactly(1, 2, 3, 7);
    }

    @Test
    void edgeCases() {
        assertThat(DistinctSortedInPlace.compact(new int[0])).isZero();
        assertThat(DistinctSortedInPlace.compact(new int[] {5, 5, 5})).isEqualTo(1);
    }
}
