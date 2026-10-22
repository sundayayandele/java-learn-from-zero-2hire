package ng.itlearning.ledger.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ng.itlearning.ledger.account.Account;
import ng.itlearning.ledger.statement.LedgerQueries.StatementLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request and response bodies. Entities never leave the service (topic 4.2). */
public final class Dtos {

    private static final String ACCOUNT_NUMBER = "\\d{10}";
    private static final String REFERENCE = "[A-Za-z0-9_-]{1,60}";

    public record OpenAccountRequest(
            @NotBlank @Size(max = 200) String holderName,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal dailyLimit) { }

    public record DepositRequest(
            @NotBlank @Pattern(regexp = ACCOUNT_NUMBER) String accountNumber,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank @Pattern(regexp = REFERENCE) String reference,
            @Size(max = 140) String narration) { }

    public record TransferRequest(
            @NotBlank @Pattern(regexp = ACCOUNT_NUMBER) String fromAccount,
            @NotBlank @Pattern(regexp = ACCOUNT_NUMBER) String toAccount,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank @Pattern(regexp = REFERENCE) String reference,
            @Size(max = 140) String narration) { }

    public record AccountResponse(String number, String holderName, String currency, String status,
                                  BigDecimal balance, BigDecimal dailyLimit, Instant openedAt) {
        static AccountResponse from(Account a) {
            return new AccountResponse(a.getNumber(), a.getHolderName(), a.getCurrency(), a.getStatus().name(),
                    a.getBalance(), a.getDailyLimit(), a.getOpenedAt());
        }
    }

    public record StatementResponse(String accountNumber, String currency, LocalDate from, LocalDate to,
                                    BigDecimal openingBalance, BigDecimal closingBalance, List<StatementLine> lines,
                                    int page, int size, long totalLines) { }

    private Dtos() { }
}
