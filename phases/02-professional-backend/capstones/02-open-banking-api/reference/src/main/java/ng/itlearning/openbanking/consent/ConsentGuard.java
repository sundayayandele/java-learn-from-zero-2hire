package ng.itlearning.openbanking.consent;

import ng.itlearning.openbanking.audit.AccessLog;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Set;

/**
 * The single gate for every account-information request (ADR 0003): right TPP, authorised, not expired,
 * permission granted, account included. Every decision is written to the access log.
 */
@Component
public class ConsentGuard {

    private final ConsentRepository consents;
    private final AccessLog log;
    private final Clock clock;

    public ConsentGuard(ConsentRepository consents, AccessLog log, Clock clock) {
        this.consents = consents;
        this.log = log;
        this.clock = clock;
    }

    /** @param accountId the account being read, or null for the account list */
    public Grant check(String consentId, String tppId, Set<Permission> anyOf, String endpoint, String accountId) {
        Consent consent = consentId == null ? null : consents.find(consentId).orElse(null);
        if (consent == null || !consent.tppId().equals(tppId)) {
            // Another TPP's consent looks exactly like a missing one.
            log.record(tppId, consentId, null, endpoint, accountId, false, "consent-not-found");
            throw ObException.consentNotFound();
        }
        Set<String> accounts = consents.accounts(consent.id());
        String failure = switch (consent.statusAt(clock.instant())) {
            case Authorised -> !consent.grantsAny(anyOf) ? "permission-not-granted"
                    : accountId != null && !accounts.contains(accountId) ? "account-not-in-consent" : null;
            case Expired -> "consent-expired";
            default -> "consent-not-authorised";
        };
        log.record(tppId, consent.id(), consent.customerId(), endpoint, accountId, failure == null, failure);
        if (failure != null) {
            throw ObException.forbidden(failure, switch (failure) {
                case "permission-not-granted" -> "The consent does not include the permission this endpoint needs.";
                case "account-not-in-consent" -> "The consent does not cover this account.";
                case "consent-expired" -> "The consent has expired.";
                default -> "The consent is not authorised.";
            });
        }
        return new Grant(consent, accounts);
    }

    public record Grant(Consent consent, Set<String> accountIds) {
    }
}
