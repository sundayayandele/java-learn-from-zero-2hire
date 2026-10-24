package ng.itlearning.lab.fees.spec;

import ng.itlearning.lab.fees.TransferFeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** The acceptance criteria of ITL-388, written as tests by the reviewer. Don't change these; fix the code. */
class FeePolicySpecTest {

    private final TransferFeeService service = new TransferFeeService();

    @ParameterizedTest(name = "NGN {0} -> {1}")
    @CsvSource({"0.01, 10.75", "5000.00, 10.75", "5000.01, 26.88", "50000.00, 26.88", "50000.01, 53.75", "9999999.99, 53.75"})
    void criterion1_ngnTiersIncludingBoundaries(String amount, String fee) {
        assertThat(service.fee(new BigDecimal(amount), "NGN")).isEqualByComparingTo(fee);
    }

    @ParameterizedTest(name = "USD {0} -> {1}")
    @CsvSource({"201.00, 1.00", "207.00, 1.04", "225.00, 1.12", "100.00, 1.00", "9000.00, 25.00", "1000.10, 5.00"})
    void criterion2_usdPercentageWithMinMaxAndHalfEven(String amount, String fee) {
        assertThat(service.fee(new BigDecimal(amount), "USD")).isEqualTo(new BigDecimal(fee));
    }

    @Test
    void criterion3_otherCurrenciesAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.fee(new BigDecimal("100"), "EUR"));
        assertThatIllegalArgumentException().isThrownBy(() -> service.authorise("0123456789", new BigDecimal("100"), "GBP"));
    }

    @Test
    void criterion4_dailyLimitHoldsUnderConcurrency() throws InterruptedException {
        AtomicInteger approved = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(64)) {
            for (int i = 0; i < 5_000; i++) {
                pool.submit(() -> {
                    start.await();
                    if (service.authorise("0123456789", new BigDecimal("10000.00"), "NGN")) {
                        approved.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        // Each transfer costs 10,026.88 including the fee: 498 fit in 5,000,000.00 (4,993,386.24), the 499th doesn't.
        assertThat(approved.get()).isEqualTo(498);
        assertThat(service.authorise("0123456789", new BigDecimal("1.00"), "NGN")).isTrue();      // + 11.75 still fits
    }

    @Test
    void criterion5_logsNeverContainFullAccountNumbers() {
        Logger logger = Logger.getLogger(TransferFeeService.class.getName());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Handler handler = new StreamHandler(out, new SimpleFormatter()) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        try {
            service.authorise("0123456789", new BigDecimal("1000.00"), "NGN");
        } finally {
            logger.removeHandler(handler);
        }
        assertThat(out.toString()).doesNotContain("0123456789");
    }
}
