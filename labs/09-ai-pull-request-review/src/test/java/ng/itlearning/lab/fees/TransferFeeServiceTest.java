package ng.itlearning.lab.fees;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests written by the AI assistant in PR #214. They all pass. Are they any good? */
class TransferFeeServiceTest {

    private final TransferFeeService service = new TransferFeeService();

    @Test
    void feeIsNotNull() {
        assertThat(service.fee(new BigDecimal("1000"), "NGN")).isNotNull();
    }

    @Test
    void feeIsPositive() {
        assertThat(service.fee(new BigDecimal("20000"), "NGN")).isPositive();
    }

    @Test
    void usdFeeWorks() {
        assertThat(service.fee(new BigDecimal("100"), "USD")).isNotNull();
    }

    @Test
    void authoriseWorks() {
        assertThat(service.authorise("0123456789", new BigDecimal("1000"), "NGN")).isTrue();
    }
}
