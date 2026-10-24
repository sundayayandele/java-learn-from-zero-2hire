package ng.itlearning.openbanking.web;

import ng.itlearning.openbanking.consent.Consent;
import ng.itlearning.openbanking.consent.Permission;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

public record ConsentView(String consentId, String status, Set<Permission> permissions, Instant expirationDateTime,
                          Instant transactionFromDateTime, Instant transactionToDateTime, String tppId,
                          Set<String> accountIds, Instant creationDateTime) {

    static ConsentView of(Consent c, Set<String> accounts, Instant now) {
        return new ConsentView(c.id(), c.statusAt(now).name(), c.permissions(), c.expiresAt(), c.txFrom(), c.txTo(),
                c.tppId(), new TreeSet<>(accounts), c.createdAt());
    }
}
