package ng.itlearning.practice.graphs;

import ng.itlearning.practice.graphs.LinkedAccountGroups.Transfer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinkedAccountGroupsTest {

    @Test
    void countsGroups() {
        assertThat(LinkedAccountGroups.groups(List.of(new Transfer("A", "B"), new Transfer("C", "B"),
                new Transfer("D", "E"), new Transfer("F", "F")))).isEqualTo(3);
        assertThat(LinkedAccountGroups.groups(List.of())).isZero();
    }

    @Test
    void longChainIsOneGroup() {
        List<Transfer> chain = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) {
            chain.add(new Transfer("acc" + i, "acc" + (i + 1)));
        }
        assertThat(LinkedAccountGroups.groups(chain)).isEqualTo(1);
    }
}
