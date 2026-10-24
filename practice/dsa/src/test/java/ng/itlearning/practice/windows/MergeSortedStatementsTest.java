package ng.itlearning.practice.windows;

import ng.itlearning.practice.windows.MergeSortedStatements.Entry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MergeSortedStatementsTest {

    @Test
    void mergesStably() {
        var a = List.of(new Entry(1, "a1"), new Entry(5, "a5"), new Entry(5, "a5b"));
        var b = List.of(new Entry(2, "b2"), new Entry(5, "b5"), new Entry(9, "b9"));
        assertThat(MergeSortedStatements.merge(a, b)).extracting(Entry::reference)
                .containsExactly("a1", "b2", "a5", "a5b", "b5", "b9");
    }

    @Test
    void emptySides() {
        assertThat(MergeSortedStatements.merge(List.of(), List.of(new Entry(1, "x")))).hasSize(1);
        assertThat(MergeSortedStatements.merge(List.of(), List.of())).isEmpty();
    }
}
