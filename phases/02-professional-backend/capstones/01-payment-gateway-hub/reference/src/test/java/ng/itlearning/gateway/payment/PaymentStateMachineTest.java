package ng.itlearning.gateway.payment;

import ng.itlearning.gateway.provider.ProviderOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static ng.itlearning.gateway.payment.PaymentStatus.ABANDONED;
import static ng.itlearning.gateway.payment.PaymentStatus.PARTIALLY_REFUNDED;
import static ng.itlearning.gateway.payment.PaymentStatus.PENDING;
import static ng.itlearning.gateway.payment.PaymentStatus.REFUNDED;
import static ng.itlearning.gateway.payment.PaymentStatus.SUCCEEDED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStateMachineTest {

    @ParameterizedTest(name = "{0} -> {1} legal={2}")
    @CsvSource({
            "INITIATED, PENDING, true", "INITIATED, FAILED, true", "INITIATED, SUCCEEDED, false",
            "PENDING, SUCCEEDED, true", "PENDING, FAILED, true", "PENDING, ABANDONED, true", "PENDING, REFUNDED, false",
            "SUCCEEDED, PENDING, false", "SUCCEEDED, FAILED, false", "SUCCEEDED, PARTIALLY_REFUNDED, true", "SUCCEEDED, REFUNDED, true",
            "PARTIALLY_REFUNDED, REFUNDED, true", "PARTIALLY_REFUNDED, PARTIALLY_REFUNDED, true", "PARTIALLY_REFUNDED, SUCCEEDED, false",
            "FAILED, SUCCEEDED, false", "ABANDONED, SUCCEEDED, false", "REFUNDED, PARTIALLY_REFUNDED, false"
    })
    void transitions(PaymentStatus from, PaymentStatus to, boolean legal) {
        assertThat(PaymentStateMachine.canMove(from, to)).isEqualTo(legal);
    }

    @Test
    void providerResultsMovePendingPayments() {
        assertThat(PaymentStateMachine.next(PENDING, ProviderOutcome.SUCCESS)).contains(SUCCEEDED);
        assertThat(PaymentStateMachine.next(PENDING, ProviderOutcome.ABANDONED)).contains(ABANDONED);
    }

    @Test
    void lateOrRepeatedResultsAreIgnored() {
        assertThat(PaymentStateMachine.next(SUCCEEDED, ProviderOutcome.ONGOING)).isEmpty();
        assertThat(PaymentStateMachine.next(SUCCEEDED, ProviderOutcome.SUCCESS)).isEmpty();
        assertThat(PaymentStateMachine.next(PENDING, ProviderOutcome.ONGOING)).isEmpty();
        assertThat(PaymentStateMachine.next(ABANDONED, ProviderOutcome.SUCCESS)).isEmpty();
    }

    @Test
    void refundsMoveToPartialThenFull() {
        assertThat(PaymentStateMachine.afterRefund(SUCCEEDED, 10_000, 4_000)).isEqualTo(PARTIALLY_REFUNDED);
        assertThat(PaymentStateMachine.afterRefund(PARTIALLY_REFUNDED, 10_000, 10_000)).isEqualTo(REFUNDED);
        assertThatThrownBy(() -> PaymentStateMachine.afterRefund(PENDING, 10_000, 1))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void providerStatusStringsAreNormalised() {
        assertThat(ProviderOutcome.fromProviderStatus("success")).isEqualTo(ProviderOutcome.SUCCESS);
        assertThat(ProviderOutcome.fromProviderStatus("FAILED")).isEqualTo(ProviderOutcome.FAILED);
        assertThat(ProviderOutcome.fromProviderStatus("abandoned")).isEqualTo(ProviderOutcome.ABANDONED);
        assertThat(ProviderOutcome.fromProviderStatus("processing")).isEqualTo(ProviderOutcome.ONGOING);
        assertThat(ProviderOutcome.fromProviderStatus(null)).isEqualTo(ProviderOutcome.ONGOING);
    }

    @Test
    void cursorsRoundTripAndRejectGarbage() {
        Cursor c = new Cursor(java.time.Instant.parse("2026-10-01T10:15:30Z"), 42);
        assertThat(Cursor.decode(c.encode())).isEqualTo(c);
        assertThatThrownBy(() -> Cursor.decode("not-a-cursor")).isInstanceOf(GatewayException.class);
    }
}
