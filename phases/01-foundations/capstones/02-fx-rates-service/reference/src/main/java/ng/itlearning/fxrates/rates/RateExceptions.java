package ng.itlearning.fxrates.rates;

/** Domain exceptions for rate lookups; each maps to one Problem Details type. */
public final class RateExceptions {

    public static final class CurrencyNotFound extends RuntimeException {
        public CurrencyNotFound(String code) { super("'" + code + "' is not an ISO 4217 currency code"); }
    }

    public static final class RateNotAvailable extends RuntimeException {
        public RateNotAvailable(String message) { super(message); }
    }

    public static final class DateOutOfRange extends RuntimeException {
        public DateOutOfRange(String message) { super(message); }
    }

    private RateExceptions() { }
}
