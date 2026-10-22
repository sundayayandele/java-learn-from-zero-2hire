package ng.itlearning.ledger.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ng.itlearning.ledger.account.Account;
import ng.itlearning.ledger.account.AccountNotFoundException;
import ng.itlearning.ledger.account.AccountRepository;
import ng.itlearning.ledger.config.LedgerProperties;
import ng.itlearning.ledger.journal.LedgerService;
import ng.itlearning.ledger.statement.LedgerQueries;
import ng.itlearning.ledger.web.Dtos.AccountResponse;
import ng.itlearning.ledger.web.Dtos.OpenAccountRequest;
import ng.itlearning.ledger.web.Dtos.StatementResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final LedgerService ledger;
    private final AccountRepository accounts;
    private final LedgerQueries queries;
    private final LedgerProperties properties;

    public AccountController(LedgerService ledger, AccountRepository accounts, LedgerQueries queries, LedgerProperties properties) {
        this.ledger = ledger;
        this.accounts = accounts;
        this.queries = queries;
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        Account account = ledger.openAccount(request.holderName(), request.currency(), request.dailyLimit());
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getNumber()))
                .body(AccountResponse.from(account));
    }

    @GetMapping("/{number}")
    public AccountResponse get(@PathVariable String number) {
        return AccountResponse.from(find(number));
    }

    @PostMapping("/{number}/freeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void freeze(@PathVariable String number) {
        ledger.freeze(number);
    }

    @PostMapping("/{number}/unfreeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfreeze(@PathVariable String number) {
        ledger.unfreeze(number);
    }

    @GetMapping("/{number}/statement")
    @Transactional(readOnly = true)
    public StatementResponse statement(@PathVariable String number,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
        Account account = find(number);
        Instant start = from.atStartOfDay(properties.businessZone()).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(properties.businessZone()).toInstant();
        BigDecimal opening = queries.balanceBefore(account.getId(), start);
        BigDecimal closing = queries.balanceBefore(account.getId(), end);
        long total = queries.statementLineCount(account.getId(), start, end);
        return new StatementResponse(account.getNumber(), account.getCurrency(), from, to, opening, closing,
                queries.statementPage(account.getId(), start, end, opening, page, size), page, size, total);
    }

    private Account find(String number) {
        return accounts.findByNumber(number)
                .filter(a -> a.getKind() == Account.Kind.CUSTOMER)
                .orElseThrow(() -> new AccountNotFoundException(number));
    }
}
