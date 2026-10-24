package ng.itlearning.gateway.payment;

import java.time.Instant;

public record Payment(long id, String reference, String merchantId, long amountMinor, String currency, String email,
                      String provider, PaymentStatus status, String authorizationUrl, long refundedMinor,
                      Instant createdAt, Instant updatedAt) {
}
