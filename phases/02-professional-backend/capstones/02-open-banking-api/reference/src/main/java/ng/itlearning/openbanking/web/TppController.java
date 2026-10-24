package ng.itlearning.openbanking.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ng.itlearning.openbanking.account.AccountInfoService;
import ng.itlearning.openbanking.consent.Consent;
import ng.itlearning.openbanking.consent.ConsentService;
import ng.itlearning.openbanking.consent.Permission;
import ng.itlearning.openbanking.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Endpoints for third-party providers (client credentials tokens with the {@code accounts} scope). */
@Validated
@RestController
@RequestMapping("/open-banking/v1")
@PreAuthorize("hasAuthority('SCOPE_accounts')")
public class TppController {

    private final ConsentService consents;
    private final AccountInfoService info;
    private final Clock clock;

    public TppController(ConsentService consents, AccountInfoService info, Clock clock) {
        this.consents = consents;
        this.info = info;
        this.clock = clock;
    }

    @PostMapping("/account-access-consents")
    ResponseEntity<ConsentView> create(@Valid @RequestBody CreateConsent body) {
        Consent c = consents.create(CurrentUser.clientId(), body.permissions(), body.expirationDateTime(),
                body.transactionFromDateTime(), body.transactionToDateTime());
        return ResponseEntity.created(URI.create("/open-banking/v1/account-access-consents/" + c.id()))
                .body(ConsentView.of(c, Set.of(), clock.instant()));
    }

    @GetMapping("/account-access-consents/{consentId}")
    ConsentView get(@PathVariable String consentId) {
        Consent c = consents.forTpp(CurrentUser.clientId(), consentId);
        return ConsentView.of(c, consents.accountIds(c.id()), clock.instant());
    }

    @DeleteMapping("/account-access-consents/{consentId}")
    ResponseEntity<Void> withdraw(@PathVariable String consentId) {
        consents.withdraw(CurrentUser.clientId(), consentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/accounts")
    List<AccountInfoService.AccountView> accounts(@RequestHeader(name = "x-consent-id", required = false) String consentId,
                                                  @RequestHeader(name = "x-fapi-customer-ip-address", required = false) String customerIp) {
        return info.accounts(CurrentUser.clientId(), consentId, customerIp != null);
    }

    @GetMapping("/accounts/{accountId}/balances")
    AccountInfoService.BalanceView balances(@PathVariable String accountId,
                                            @RequestHeader(name = "x-consent-id", required = false) String consentId,
                                            @RequestHeader(name = "x-fapi-customer-ip-address", required = false) String customerIp) {
        return info.balances(CurrentUser.clientId(), consentId, accountId, customerIp != null);
    }

    @GetMapping("/accounts/{accountId}/transactions")
    AccountInfoService.TransactionPage transactions(@PathVariable String accountId,
                                                    @RequestHeader(name = "x-consent-id", required = false) String consentId,
                                                    @RequestHeader(name = "x-fapi-customer-ip-address", required = false) String customerIp,
                                                    @RequestParam(required = false) Instant fromBookingDateTime,
                                                    @RequestParam(required = false) Instant toBookingDateTime,
                                                    @RequestParam(required = false) String cursor,
                                                    @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return info.transactions(CurrentUser.clientId(), consentId, accountId, fromBookingDateTime, toBookingDateTime,
                cursor, limit, customerIp != null);
    }

    public record CreateConsent(@NotEmpty Set<Permission> permissions, @NotNull Instant expirationDateTime,
                                Instant transactionFromDateTime, Instant transactionToDateTime) {
    }
}
