package ng.itlearning.practice.design;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentTallyTest {

    @Test
    void noLostUpdatesUnderContention() throws InterruptedException {
        var tally = new ConcurrentTally();
        try (var pool = Executors.newFixedThreadPool(16)) {
            for (int t = 0; t < 16; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < 10_000; i++) {
                        tally.record(i % 2 == 0 ? "Jumia" : "Bolt", 100);
                    }
                });
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(tally.total("Jumia")).isEqualTo(8_000_000);
        assertThat(tally.total("Bolt")).isEqualTo(8_000_000);
        assertThat(tally.total("Uber")).isZero();
    }
}
