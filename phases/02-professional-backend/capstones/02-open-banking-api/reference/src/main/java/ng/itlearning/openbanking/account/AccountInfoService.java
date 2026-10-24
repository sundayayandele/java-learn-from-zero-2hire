package ng.itlearning.openbanking.account;

import ng.itlearning.openbanking.config.OpenBankingProperties;
import ng.itlearning.openbanking.consent.ConsentGuard;
import ng.itlearning.openbanking.consent.ConsentRepository;
import ng.itlearning.openbanking.consent.ObException;
import ng.itlearning.openbanking.consent.Permission;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Account information for TPPs: always through the consent guard, with least data and the unattended-access limit. */
@Service
public class AccountInfoService {

    private static final Set<Permission> ACCOUNTS = EnumSet.of(Permission.ReadAccountsBasic, Permission.ReadAccountsDetail);
    private static final Set<Permission> BALANCES = EnumSet.of(Permission.ReadBalances);
    private static final Set<Permission> TRANSACTIONS = EnumSet.of(Permission.ReadTransactionsBasic, Permission.ReadTransactionsDetail);

    private final ConsentGuard guard;
    private final AccountRepository accounts;
    private final ConsentRepository consents;
    private final OpenBankingProperties props;
    private final Clock clock;

    public AccountInfoService(ConsentGuard guard, AccountRepository accounts, ConsentRepository consents,
                              OpenBankingProperties props, Clock clock) {
        this.guard = guard;
        this.accounts = accounts;
        this.consents = consents;
        this.props = props;
        this.clock = clock;
    }

    public List<AccountView> accounts(String tppId, String consentId, boolean customerPresent) {
        var grant = guard.check(consentId, tppId, ACCOUNTS, "accounts", null);
        boolean detail = grant.consent().permissions().contains(Permission.ReadAccountsDetail);
        return accounts.byIds(grant.accountIds()).stream().map(a -> {
            limit(consentId, a.id(), "accounts", customerPresent);
            return new AccountView(a.id(), a.type(), a.currency(), a.nickname(), detail ? a.number() : null);
        }).toList();
    }

    public BalanceView balances(String tppId, String consentId, String accountId, boolean customerPresent) {
        guard.check(consentId, tppId, BALANCES, "balances", accountId);
        limit(consentId, accountId, "balances", customerPresent);
        var a = accounts.byIds(List.of(accountId)).getFirst();
        return new BalanceView(a.id(), a.currency(), a.booked(), a.available(), clock.instant());
    }

    /** Only transactions inside both the consent's window and the requested window are ever read. */
    public TransactionPage transactions(String tppId, String consentId, String accountId, Instant from, Instant to,
                                        String cursor, int limit, boolean customerPresent) {
        var grant = guard.check(consentId, tppId, TRANSACTIONS, "transactions", accountId);
        limit(consentId, accountId, "transactions", customerPresent);
        var consent = grant.consent();
        Instant start = latest(consent.txFrom(), from);
        Instant end = earliest(consent.txTo() == null ? clock.instant() : consent.txTo(), to == null ? clock.instant() : to);
        boolean detail = consent.permissions().contains(Permission.ReadTransactionsDetail);
        Instant afterAt = null;
        Long afterId = null;
        if (cursor != null) {
            String[] parts = cursor.split("_");
            try {
                afterAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
                afterId = Long.parseLong(parts[1]);
            } catch (RuntimeException e) {
                throw new ObException(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid cursor.");
            }
        }
        if (!start.isBefore(end)) {
            return new TransactionPage(List.of(), null);
        }
        var rows = accounts.transactions(accountId, start, end, afterAt, afterId, limit + 1);
        boolean more = rows.size() > limit;
        var page = more ? rows.subList(0, limit) : rows;
        String next = more ? page.getLast().bookedAt().toEpochMilli() + "_" + page.getLast().id() : null;
        return new TransactionPage(page.stream().map(t -> new TransactionView(t.id(), t.bookedAt(), t.amount(), t.creditDebit(),
                t.balanceAfter(), detail ? t.counterpartyName() : null, detail ? t.counterpartyAccount() : null,
                detail ? t.narration() : null)).toList(), next);
    }

    /** At most N unattended calls per consent, account and endpoint per (Lagos) day; customer-present calls are free. */
    private void limit(String consentId, String accountId, String endpoint, boolean customerPresent) {
        if (customerPresent) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(props.zone()));
        LocalDate day = now.toLocalDate();
        int calls = consents.countCall(consentId, accountId, endpoint, day);
        if (calls > props.unattendedDailyLimit()) {
            Duration untilMidnight = Duration.between(now, day.plusDays(1).atStartOfDay(props.zone()));
            throw new ObException(HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded",
                    "Unattended access limit reached for this account today.", untilMidnight);
        }
    }

    private static Instant latest(Instant a, Instant b) {
        return b == null || a.isAfter(b) ? a : b;
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    public record AccountView(String accountId, String accountType, String currency, String nickname, String accountNumber) {
    }

    public record BalanceView(String accountId, String currency, java.math.BigDecimal booked, java.math.BigDecimal available,
                              Instant asOf) {
    }

    public record TransactionView(long transactionId, Instant bookingDateTime, java.math.BigDecimal amount, String creditDebit,
                                  java.math.BigDecimal balanceAfter, String counterpartyName, String counterpartyAccount,
                                  String narration) {
    }

    public record TransactionPage(List<TransactionView> transactions, String nextCursor) {
    }
}
