package ng.itlearning.ledger.journal;

/** A request that is well-formed but breaks a ledger rule. Each subtype maps to its own Problem Details type. */
public abstract sealed class LedgerRuleException extends RuntimeException
        permits LedgerRuleException.DailyLimitExceeded, LedgerRuleException.SameAccount,
                LedgerRuleException.CurrencyMismatch, LedgerRuleException.UnsupportedCurrency {

    protected LedgerRuleException(String message) { super(message); }

    public static final class DailyLimitExceeded extends LedgerRuleException {
        public DailyLimitExceeded() { super("This transfer would exceed the account's daily limit"); }
    }

    public static final class SameAccount extends LedgerRuleException {
        public SameAccount() { super("Source and destination accounts must differ"); }
    }

    public static final class CurrencyMismatch extends LedgerRuleException {
        public CurrencyMismatch() { super("Both accounts must use the same currency"); }
    }

    public static final class UnsupportedCurrency extends LedgerRuleException {
        public UnsupportedCurrency(String currency) { super("Currency " + currency + " is not supported by this ledger"); }
    }
}
