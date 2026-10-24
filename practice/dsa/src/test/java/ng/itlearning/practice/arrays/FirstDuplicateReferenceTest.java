package ng.itlearning.practice.arrays;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FirstDuplicateReferenceTest {

    @Test
    void findsTheFirstRepeat() {
        assertThat(FirstDuplicateReference.find(List.of("TRF1", "TRF2", "TRF3", "TRF2", "TRF1"))).contains("TRF2");
    }

    @Test
    void ignoresCaseAndSpaces() {
        assertThat(FirstDuplicateReference.find(List.of("abc-1", " ABC-2", "Abc-2 "))).contains("ABC-2");
    }

    @Test
    void emptyWhenUnique() {
        assertThat(FirstDuplicateReference.find(List.of("A", "B", "C"))).isEmpty();
        assertThat(FirstDuplicateReference.find(List.of())).isEmpty();
    }
}
