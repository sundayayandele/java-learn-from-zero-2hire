package ng.itlearning.openbanking.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import ng.itlearning.openbanking.consent.ConsentService;
import ng.itlearning.openbanking.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

/** The bank's own channel: customers approve, reject and revoke consents for their accounts. */
@RestController
@RequestMapping("/customer/v1/consents")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerController {

    private final ConsentService consents;
    private final Clock clock;

    public CustomerController(ConsentService consents, Clock clock) {
        this.consents = consents;
        this.clock = clock;
    }

    @GetMapping
    List<ConsentView> mine() {
        return consents.forCustomer(CurrentUser.username()).stream()
                .map(c -> ConsentView.of(c, consents.accountIds(c.id()), clock.instant())).toList();
    }

    @PostMapping("/{consentId}/authorise")
    ConsentView authorise(@PathVariable String consentId, @Valid @RequestBody Authorise body) {
        var c = consents.authorise(CurrentUser.username(), consentId, body.accountIds());
        return ConsentView.of(c, consents.accountIds(c.id()), clock.instant());
    }

    @PostMapping("/{consentId}/reject")
    ResponseEntity<Void> reject(@PathVariable String consentId) {
        consents.reject(CurrentUser.username(), consentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{consentId}/revoke")
    ResponseEntity<Void> revoke(@PathVariable String consentId) {
        consents.revoke(CurrentUser.username(), consentId);
        return ResponseEntity.noContent().build();
    }

    public record Authorise(@NotEmpty List<String> accountIds) {
    }
}
