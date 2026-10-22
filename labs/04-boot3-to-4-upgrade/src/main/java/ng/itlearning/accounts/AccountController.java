package ng.itlearning.accounts;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    public record AccountResponse(String number, String holderName, BigDecimal balance, LocalDate openedOn) { }

    private final AccountRepository accounts;
    private final NotificationClient notifications;

    public AccountController(AccountRepository accounts, NotificationClient notifications) {
        this.accounts = accounts;
        this.notifications = notifications;
    }

    @GetMapping("/{number}")
    public AccountResponse get(@PathVariable String number) {
        Account a = accounts.findByNumber(number)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        notifications.accountViewed(number);
        return new AccountResponse(a.getNumber(), a.getHolderName(), a.getBalance(), a.getOpenedOn());
    }
}
