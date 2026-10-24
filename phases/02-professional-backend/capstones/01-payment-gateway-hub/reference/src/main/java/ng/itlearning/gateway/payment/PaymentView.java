package ng.itlearning.gateway.payment;

import java.time.Instant;
import java.util.List;

/** What merchants see. Stored as JSON for idempotent replays, so it must be stable. */
public record PaymentView(String reference, long amount, String currency, String status, String provider,
                          String authorizationUrl, long refunded, Instant createdAt, List<PaymentEvent> history) {

    static PaymentView of(Payment p, List<PaymentEvent> history) {
        return new PaymentView(p.reference(), p.amountMinor(), p.currency(), p.status().name(), p.provider(),
                p.authorizationUrl(), p.refundedMinor(), p.createdAt(), history);
    }
}
