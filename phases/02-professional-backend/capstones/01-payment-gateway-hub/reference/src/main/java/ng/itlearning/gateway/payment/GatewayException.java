package ng.itlearning.gateway.payment;

import org.springframework.http.HttpStatus;

import java.time.Duration;

/** A business error with its HTTP status and a stable Problem Details type (topic 5.3). */
public class GatewayException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final Duration retryAfter;

    public GatewayException(HttpStatus status, String type, String detail) {
        this(status, type, detail, null);
    }

    public GatewayException(HttpStatus status, String type, String detail, Duration retryAfter) {
        super(detail);
        this.status = status;
        this.type = type;
        this.retryAfter = retryAfter;
    }

    public HttpStatus status() {
        return status;
    }

    public String type() {
        return type;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public static GatewayException notFound(String reference) {
        return new GatewayException(HttpStatus.NOT_FOUND, "payment-not-found", "No payment " + reference + ".");
    }

    public static GatewayException invalidTransition(PaymentStatus from, PaymentStatus to) {
        return new GatewayException(HttpStatus.CONFLICT, "invalid-state-transition",
                "A " + from + " payment can't become " + to + ".");
    }
}
