package ng.itlearning.gateway.payment;

import ng.itlearning.gateway.provider.ProviderOutcome;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ng.itlearning.gateway.payment.PaymentStatus.ABANDONED;
import static ng.itlearning.gateway.payment.PaymentStatus.FAILED;
import static ng.itlearning.gateway.payment.PaymentStatus.INITIATED;
import static ng.itlearning.gateway.payment.PaymentStatus.PARTIALLY_REFUNDED;
import static ng.itlearning.gateway.payment.PaymentStatus.PENDING;
import static ng.itlearning.gateway.payment.PaymentStatus.REFUNDED;
import static ng.itlearning.gateway.payment.PaymentStatus.SUCCEEDED;

/**
 * The only place that knows which status changes are legal (see the brief's diagram).
 * Anything else, such as a late "ongoing" after SUCCEEDED, is ignored rather than applied.
 */
public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> LEGAL = Map.of(
            INITIATED, EnumSet.of(PENDING, FAILED),
            PENDING, EnumSet.of(SUCCEEDED, FAILED, ABANDONED),
            SUCCEEDED, EnumSet.of(PARTIALLY_REFUNDED, REFUNDED),
            PARTIALLY_REFUNDED, EnumSet.of(PARTIALLY_REFUNDED, REFUNDED),
            FAILED, EnumSet.noneOf(PaymentStatus.class),
            ABANDONED, EnumSet.noneOf(PaymentStatus.class),
            REFUNDED, EnumSet.noneOf(PaymentStatus.class));

    private PaymentStateMachine() {
    }

    public static boolean canMove(PaymentStatus from, PaymentStatus to) {
        return LEGAL.get(from).contains(to);
    }

    /** The status a provider result moves the payment to, or empty if the result must not change it. */
    public static Optional<PaymentStatus> next(PaymentStatus current, ProviderOutcome outcome) {
        PaymentStatus target = switch (outcome) {
            case SUCCESS -> SUCCEEDED;
            case FAILED -> FAILED;
            case ABANDONED -> ABANDONED;
            case ONGOING -> PENDING;
        };
        return current != target && canMove(current, target) ? Optional.of(target) : Optional.empty();
    }

    /** Refunding moves to REFUNDED when the total reaches the amount, otherwise PARTIALLY_REFUNDED. */
    public static PaymentStatus afterRefund(PaymentStatus current, long amount, long refundedAfter) {
        PaymentStatus target = refundedAfter >= amount ? REFUNDED : PARTIALLY_REFUNDED;
        if (!canMove(current, target)) {
            throw GatewayException.invalidTransition(current, target);
        }
        return target;
    }
}
