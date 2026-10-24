package ng.itlearning.practice.dp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FewestNotesTest {

    @Test
    void fewestNotes() {
        assertThat(FewestNotes.fewest(new int[] {200, 500, 1000}, 1700)).isEqualTo(3);
        assertThat(FewestNotes.fewest(new int[] {1, 3, 4}, 6)).isEqualTo(2);    // greedy would give 4+1+1
        assertThat(FewestNotes.fewest(new int[] {500}, 300)).isEqualTo(-1);
        assertThat(FewestNotes.fewest(new int[] {500}, 0)).isZero();
    }
}
