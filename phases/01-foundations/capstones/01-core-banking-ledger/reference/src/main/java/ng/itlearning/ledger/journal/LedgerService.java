package ng.itlearning.ledger.journal;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import ng.itlearning.ledger.account.Account;
import ng.itlearning.ledger.account.AccountNotFoundException;
import ng.itlearning.ledger.account.AccountNumbers;
import ng.itlearning.ledger.account.AccountRepository;
import ng.itlearning.ledger.config.LedgerProperties;
import ng.itlearning.ledger.statement.LedgerQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Posts deposits, transfers and reversals as balanced journals.
 *
 * <p>Concurrency: every method locks the customer accounts it touches ({@code SELECT ... FOR UPDATE}, in id order)
 * before reading balances, and locks internal accounts last. Two requests that touch the same account are
 * therefore serialised by the database, across any number of service instances, and can't deadlock.
 *
 * <p>Idempotency: the reference is checked after the locks are held, so a retried request sees the committed
 * result of the first one and gets the original receipt back.
 */
@Service
public class LedgerService {

    public record DepositCommand(String accountNumber, BigDecimal amount, String reference, String narration) { }

    public record TransferCommand(String fromAccount, String toAccount, BigDecimal amount, String reference, String narration) { }

    public record Receipt(String reference, JournalType type, BigDecimal amount, BigDecimal fee, Instant postedAt, boolean replayed) { }

    private static final Set<String> INTERNAL_ACCOUNTS =
            Set.of(AccountNumbers.CASH, AccountNumbers.FEE_INCOME, AccountNumbers.SUSPENSE);

    private final AccountRepository accounts;
    private final JournalRepository journals;
    private final LedgerQueries queries;
    private final FeeSchedule fees;
    private final LedgerProperties properties;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final DistributionSummary transferAmounts;

    public LedgerService(AccountRepository accounts, JournalRepository journals, LedgerQueries queries, FeeSchedule fees,
                         LedgerProperties properties, Clock clock, MeterRegistry metrics) {
        this.accounts = accounts;
        this.journals = journals;
        this.queries = queries;
        this.fees = fees;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
        this.transferAmounts = DistributionSummary.builder("ledger.transfer.amount")
                .description("Transfer amounts (approximate; the ledger is the record)")
                .register(metrics);
    }

    @Transactional
    public Account openAccount(String holderName, String currency, BigDecimal dailyLimit) {
        String ccy = currency == null ? "NGN" : currency;
        requireSupported(ccy);
        Account account = Account.customer(accounts.nextAccountNumber(), holderName.strip(), ccy,
                dailyLimit == null ? properties.defaultDailyLimit() : dailyLimit, clock.instant());
        return accounts.save(account);
    }

    @Transactional
    public void freeze(String number) {
        lockCustomers(List.of(number)).get(number).freeze();
    }

    @Transactional
    public void unfreeze(String number) {
        lockCustomers(List.of(number)).get(number).unfreeze();
    }

    @Transactional
    public Receipt deposit(DepositCommand cmd) {
        String hash = hash("DEPOSIT", cmd.accountNumber(), cmd.amount(), cmd.narration());
        Account customer = lockCustomers(List.of(cmd.accountNumber())).get(cmd.accountNumber());

        Optional<Receipt> replay = replayOf(cmd.reference(), hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        customer.requireActive();
        requireSupported(customer.getCurrency());

        Account cash = lockInternal(AccountNumbers.CASH);
        Journal journal = new Journal(cmd.reference(), JournalType.DEPOSIT, hash, cmd.narration(), null, clock.instant());
        journal.post(cash, Direction.D, cmd.amount());
        journal.post(customer, Direction.C, cmd.amount());
        journals.save(journal);
        metrics.counter("ledger.journals", "type", "DEPOSIT").increment();
        return new Receipt(journal.getReference(), JournalType.DEPOSIT, cmd.amount(), BigDecimal.ZERO.setScale(2),
                journal.getCreatedAt(), false);
    }

    @Transactional
    public Receipt transfer(TransferCommand cmd) {
        if (cmd.fromAccount().equals(cmd.toAccount())) {
            throw new LedgerRuleException.SameAccount();
        }
        String hash = hash("TRANSFER", cmd.fromAccount(), cmd.toAccount(), cmd.amount(), cmd.narration());
        Map<String, Account> locked = lockCustomers(List.of(cmd.fromAccount(), cmd.toAccount()));
        Account from = locked.get(cmd.fromAccount());
        Account to = locked.get(cmd.toAccount());

        Optional<Receipt> replay = replayOf(cmd.reference(), hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        from.requireActive();
        to.requireActive();
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new LedgerRuleException.CurrencyMismatch();
        }
        requireSupported(from.getCurrency());

        BigDecimal fee = fees.feeFor(cmd.amount());
        BigDecimal total = cmd.amount().add(fee);
        LocalDate today = LocalDate.now(clock.withZone(properties.businessZone()));
        BigDecimal usedToday = queries.outgoingTransfersOn(from.getId(), today, properties.businessZone());
        if (usedToday.add(total).compareTo(from.getDailyLimit()) > 0) {
            throw new LedgerRuleException.DailyLimitExceeded();
        }

        Journal journal = new Journal(cmd.reference(), JournalType.TRANSFER, hash, cmd.narration(), null, clock.instant());
        journal.post(from, Direction.D, total);               // throws InsufficientFundsException
        journal.post(to, Direction.C, cmd.amount());
        if (fee.signum() > 0) {
            journal.post(lockInternal(AccountNumbers.FEE_INCOME), Direction.C, fee);   // internal accounts locked last
        }
        journals.save(journal);
        metrics.counter("ledger.journals", "type", "TRANSFER").increment();
        transferAmounts.record(cmd.amount().doubleValue());
        return new Receipt(journal.getReference(), JournalType.TRANSFER, cmd.amount(), fee, journal.getCreatedAt(), false);
    }

    @Transactional
    public Receipt reverse(String originalReference) {
        String reversalReference = originalReference + "-REV";
        String hash = hash("REVERSAL", originalReference);

        // Lock the accounts first (found without loading entities), then load the journal, so balances are fresh.
        List<String> numbers = queries.accountNumbersOf(originalReference);
        if (numbers.isEmpty()) {
            throw new JournalNotFoundException(originalReference);
        }
        lockCustomers(numbers.stream().filter(n -> !INTERNAL_ACCOUNTS.contains(n)).toList());
        numbers.stream().filter(INTERNAL_ACCOUNTS::contains).sorted().forEach(this::lockInternal);

        Journal original = journals.findByReference(originalReference)
                .filter(j -> j.getType() == JournalType.TRANSFER)
                .orElseThrow(() -> new JournalNotFoundException(originalReference));
        if (journals.existsByReverses(original)) {
            throw new AlreadyReversedException(originalReference);
        }

        Journal reversal = new Journal(reversalReference, JournalType.REVERSAL, hash,
                "Reversal of " + originalReference, original, clock.instant());
        for (Posting p : original.getPostings()) {
            reversal.post(p.getAccount(), p.getDirection().opposite(), p.getAmount());   // may throw InsufficientFunds
        }
        journals.save(reversal);
        metrics.counter("ledger.journals", "type", "REVERSAL").increment();
        Receipt originalReceipt = receiptOf(original, false);
        return new Receipt(reversalReference, JournalType.REVERSAL, originalReceipt.amount(), originalReceipt.fee(),
                reversal.getCreatedAt(), false);
    }

    private Map<String, Account> lockCustomers(List<String> numbers) {
        Map<String, Account> found = accounts.lockByNumbers(new LinkedHashSet<>(numbers)).stream()
                .collect(Collectors.toMap(Account::getNumber, Function.identity()));
        for (String n : numbers) {
            Account a = found.get(n);
            if (a == null || a.getKind() != Account.Kind.CUSTOMER) {
                throw new AccountNotFoundException(n);
            }
        }
        return found;
    }

    private Account lockInternal(String number) {
        return accounts.lockByNumbers(List.of(number)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Internal account " + number + " is missing"));
    }

    private Optional<Receipt> replayOf(String reference, String hash) {
        return journals.findByReference(reference).map(existing -> {
            if (!existing.getRequestHash().equals(hash)) {
                throw new DuplicateReferenceException(reference);
            }
            return receiptOf(existing, true);
        });
    }

    private static Receipt receiptOf(Journal j, boolean replayed) {
        BigDecimal fee = BigDecimal.ZERO.setScale(2);
        BigDecimal amount = BigDecimal.ZERO.setScale(2);
        for (Posting p : j.getPostings()) {
            if (p.getAccount().getKind() == Account.Kind.FEE_INCOME) {
                fee = fee.add(p.getAmount());
            } else if (p.getAccount().getKind() == Account.Kind.CUSTOMER && p.getDirection() == Direction.C) {
                amount = amount.add(p.getAmount());
            }
        }
        return new Receipt(j.getReference(), j.getType(), amount, fee, j.getCreatedAt(), replayed);
    }

    private void requireSupported(String currency) {
        if (!properties.supportedCurrencies().contains(currency)) {
            throw new LedgerRuleException.UnsupportedCurrency(currency);
        }
    }

    /** A fingerprint of the request, so a reused reference with a different payload can be detected. */
    static String hash(Object... parts) {
        String canonical = java.util.Arrays.stream(parts)
                .map(p -> p instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString() : String.valueOf(p))
                .collect(Collectors.joining("|"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
