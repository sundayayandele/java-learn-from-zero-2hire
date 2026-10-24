package ng.itlearning.gateway.provider;

/** Timeouts, connection failures and 5xx responses: the provider may recover, so these count towards the circuit breaker. */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException(String provider, Throwable cause) {
        super("Provider " + provider + " is unavailable", cause);
    }

    public ProviderUnavailableException(String provider, String detail) {
        super("Provider " + provider + " is unavailable: " + detail);
    }
}
