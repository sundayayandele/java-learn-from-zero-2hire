package ng.itlearning.practice.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PeakConcurrentCustomersTest {

    @Test
    void examples() {
        assertThat(PeakConcurrentCustomers.peak(new int[] {900, 910, 915, 1000}, new int[] {920, 930, 1000, 1010})).isEqualTo(3);
        assertThat(PeakConcurrentCustomers.peak(new int[] {1, 5}, new int[] {5, 6})).isEqualTo(1);
        assertThat(PeakConcurrentCustomers.peak(new int[0], new int[0])).isZero();
    }

    @Test
    void inputArraysAreNotModified() {
        int[] a = {3, 1, 2};
        int[] d = {4, 9, 5};
        PeakConcurrentCustomers.peak(a, d);
        assertThat(a).containsExactly(3, 1, 2);
    }
}
