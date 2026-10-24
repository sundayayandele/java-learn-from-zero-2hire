package ng.itlearning.openbanking.consent;

import ng.itlearning.openbanking.account.AccountRepository;
import ng.itlearning.openbanking.config.OpenBankingProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ConsentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConsentRepository consents;
    private final AccountRepository accounts;
    private final OpenBankingProperties props;
    private final Clock clock;

    public ConsentService(ConsentRepository consents, AccountRepository accounts, OpenBankingProperties props, Clock clock) {
        this.consents = consents;
        this.accounts = accounts;
        this.props = props;
        this.clock = clock;
    }

    public Consent create(String tppId, Set<Permission> permissions, Instant expiresAt, Instant txFrom, Instant txTo) {
        Instant now = clock.instant();
        if (permissions == null || permissions.isEmpty()) {
            throw invalid("Request at least one permission.");
        }
        if (!expiresAt.isAfter(now) || expiresAt.isAfter(now.plus(props.maxConsentLifetime()))) {
            throw invalid("expirationDateTime must be in the future and at most " + props.maxConsentLifetime().toDays() + " days away.");
        }
        Instant earliest = ZonedDateTime.ofInstant(now, props.zone()).minusMonths(props.maxHistoryMonths()).toInstant();
        if (txFrom != null && txFrom.isBefore(earliest)) {
            throw invalid("transactionFromDateTime may not be more than " + props.maxHistoryMonths() + " months ago.");
        }
        if (txFrom != null && txTo != null && !txFrom.isBefore(txTo)) {
            throw invalid("transactionFromDateTime must be before transactionToDateTime.");
        }
        Consent c = new Consent("aac-" + HexFormat.of().formatHex(bytes()), tppId, null, EnumSet.copyOf(permissions),
                ConsentStatus.AwaitingAuthorisation, expiresAt, txFrom == null ? earliest : txFrom, txTo, now);
        consents.insert(c);
        return c;
    }

    public Consent forTpp(String tppId, String consentId) {
        return consents.find(consentId).filter(c -> c.tppId().equals(tppId)).orElseThrow(ObException::consentNotFound);
    }

    @Transactional
    public void withdraw(String tppId, String consentId) {
        Consent c = consents.lock(consentId).filter(x -> x.tppId().equals(tppId)).orElseThrow(ObException::consentNotFound);
        consents.updateStatus(c.id(), c.revoke(clock.instant()), null, clock.instant());
    }

    public List<Consent> forCustomer(String username) {
        return consents.forCustomer(customerId(username));
    }

    @Transactional
    public Consent authorise(String username, String consentId, List<String> accountIds) {
        long customerId = customerId(username);
        Consent c = consents.lock(consentId).orElseThrow(ObException::consentNotFound);
        if (accountIds == null || accountIds.isEmpty()) {
            throw invalid("Choose at least one account.");
        }
        Set<String> unique = new LinkedHashSet<>(accountIds);
        if (!accounts.allOwnedBy(customerId, unique)) {
            throw ObException.forbidden("not-account-owner", "You can only share your own accounts.");
        }
        ConsentStatus next = c.authorise(clock.instant());
        consents.updateStatus(c.id(), next, customerId, clock.instant());
        consents.addAccounts(c.id(), List.copyOf(unique));
        return consents.find(c.id()).orElseThrow();
    }

    @Transactional
    public void reject(String username, String consentId) {
        long customerId = customerId(username);
        Consent c = consents.lock(consentId).orElseThrow(ObException::consentNotFound);
        consents.updateStatus(c.id(), c.reject(clock.instant()), customerId, clock.instant());
    }

    @Transactional
    public void revoke(String username, String consentId) {
        long customerId = customerId(username);
        Consent c = consents.lock(consentId).filter(x -> x.customerId() != null && x.customerId() == customerId)
                .orElseThrow(ObException::consentNotFound);
        consents.updateStatus(c.id(), c.revoke(clock.instant()), null, clock.instant());
    }

    public Set<String> accountIds(String consentId) {
        return consents.accounts(consentId);
    }

    private long customerId(String username) {
        return accounts.customerIdByUsername(username)
                .orElseThrow(() -> ObException.forbidden("not-a-customer", "You are not a customer of this bank."));
    }

    private static ObException invalid(String detail) {
        return new ObException(HttpStatus.BAD_REQUEST, "validation-failed", detail);
    }

    private static byte[] bytes() {
        byte[] b = new byte[12];
        RANDOM.nextBytes(b);
        return b;
    }
}
