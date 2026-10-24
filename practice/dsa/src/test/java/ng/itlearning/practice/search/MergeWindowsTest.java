package ng.itlearning.practice.search;

import ng.itlearning.practice.search.MergeWindows.Window;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MergeWindowsTest {

    @Test
    void mergesOverlappingAndTouching() {
        var merged = MergeWindows.merge(List.of(new Window(8, 10), new Window(1, 3), new Window(2, 6),
                new Window(15, 18), new Window(6, 7)));
        assertThat(merged).containsExactly(new Window(1, 7), new Window(8, 10), new Window(15, 18));
    }

    @Test
    void containedWindowsAndEmpty() {
        assertThat(MergeWindows.merge(List.of(new Window(1, 10), new Window(2, 3)))).containsExactly(new Window(1, 10));
        assertThat(MergeWindows.merge(List.of())).isEmpty();
    }
}
