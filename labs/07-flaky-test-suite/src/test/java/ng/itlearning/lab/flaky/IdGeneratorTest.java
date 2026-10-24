package ng.itlearning.lab.flaky;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdGeneratorTest {

    @Test
    void firstReferenceIsNumberOne() {
        assertThat(IdGenerator.next()).isEqualTo("TRF-000001");
    }

    @Test
    void referencesIncrease() {
        String a = IdGenerator.next();
        String b = IdGenerator.next();
        assertThat(b).isGreaterThan(a);
    }
}
