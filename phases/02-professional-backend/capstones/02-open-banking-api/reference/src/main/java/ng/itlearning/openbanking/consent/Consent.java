package ng.itlearning.openbanking.consent;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** An account-access consent and its lifecycle rules (pure domain logic, unit-tested without Spring). */
public record Consent(String id, String tppId, Long customerId, Set<Permission> permissions, ConsentStatus storedStatus,
                      Instant expiresAt, Instant txFrom, Instant txTo, Instant createdAt) {

    public ConsentStatus statusAt(Instant now) {
        boolean live = storedStatus == ConsentStatus.Authorised || storedStatus == ConsentStatus.AwaitingAuthorisation;
        return live && !now.isBefore(expiresAt) ? ConsentStatus.Expired : storedStatus;
    }

    public ConsentStatus authorise(Instant now) {
        return move(now, ConsentStatus.AwaitingAuthorisation, ConsentStatus.Authorised);
    }

    public ConsentStatus reject(Instant now) {
        return move(now, ConsentStatus.AwaitingAuthorisation, ConsentStatus.Rejected);
    }

    /** Customers revoke authorised consents; TPPs may also withdraw ones still awaiting authorisation. */
    public ConsentStatus revoke(Instant now) {
        ConsentStatus current = statusAt(now);
        if (current != ConsentStatus.Authorised && current != ConsentStatus.AwaitingAuthorisation) {
            throw invalid(current, ConsentStatus.Revoked);
        }
        return ConsentStatus.Revoked;
    }

    public boolean grantsAny(Set<Permission> needed) {
        return needed.stream().anyMatch(permissions::contains);
    }

    private ConsentStatus move(Instant now, ConsentStatus from, ConsentStatus to) {
        ConsentStatus current = statusAt(now);
        if (current != from) {
            throw invalid(current, to);
        }
        return to;
    }

    private static ObException invalid(ConsentStatus from, ConsentStatus to) {
        return new ObException(HttpStatus.CONFLICT, "invalid-consent-transition",
                "A consent that is " + from + " can't become " + to + ".");
    }

    public static Set<Permission> parse(String csv) {
        Set<Permission> set = EnumSet.noneOf(Permission.class);
        for (String p : csv.split(",")) {
            if (!p.isBlank()) {
                set.add(Permission.valueOf(p.trim()));
            }
        }
        return set;
    }
}
