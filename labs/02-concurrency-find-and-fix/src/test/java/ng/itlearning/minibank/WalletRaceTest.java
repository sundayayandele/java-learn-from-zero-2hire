package ng.itlearning.minibank;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example of the test pattern to use for every bug: start many threads at the same instant with a latch,
 * repeat many times, and assert an invariant. This one FAILS until you fix Wallet (bug 1).
 */
class WalletRaceTest {

    @Test
    void twoConcurrentWithdrawalsCannotBothSucceedBeyondTheBalance() throws Exception {
        for (int round = 0; round < 2_000; round++) {
            Wallet wallet = new Wallet("w-1", new BigDecimal("1000.00"));
            AtomicInteger successes = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                for (int t = 0; t < 2; t++) {
                    pool.submit(() -> {
                        start.await();
                        if (wallet.withdraw(new BigDecimal("800.00"))) {
                            successes.incrementAndGet();
                        }
                        return null;
                    });
                }
                start.countDown();
            }
            assertThat(successes.get()).as("round %d", round).isLessThanOrEqualTo(1);
            assertThat(wallet.balance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }
}
