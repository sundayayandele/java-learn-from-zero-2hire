package ng.itlearning.openbanking.consent;

import org.springframework.http.HttpStatus;

import java.time.Duration;

/** A business or authorisation failure with a stable Problem Details type. */
public class ObException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final Duration retryAfter;

    public ObException(HttpStatus status, String type, String detail) {
        this(status, type, detail, null);
    }

    public ObException(HttpStatus status, String type, String detail, Duration retryAfter) {
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

    public static ObException forbidden(String type, String detail) {
        return new ObException(HttpStatus.FORBIDDEN, type, detail);
    }

    public static ObException consentNotFound() {
        return new ObException(HttpStatus.NOT_FOUND, "consent-not-found", "No such consent.");
    }
}
