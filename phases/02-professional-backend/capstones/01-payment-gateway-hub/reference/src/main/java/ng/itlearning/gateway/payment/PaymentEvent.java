package ng.itlearning.gateway.payment;

import java.time.Instant;

public record PaymentEvent(PaymentStatus fromStatus, PaymentStatus toStatus, String cause, String providerEventId,
                           String note, Instant createdAt) {
}
