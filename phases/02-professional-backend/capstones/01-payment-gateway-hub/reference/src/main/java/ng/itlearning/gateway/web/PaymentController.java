package ng.itlearning.gateway.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import ng.itlearning.gateway.payment.CreatePayment;
import ng.itlearning.gateway.payment.Cursor;
import ng.itlearning.gateway.payment.Payment;
import ng.itlearning.gateway.payment.PaymentService;
import ng.itlearning.gateway.payment.PaymentStatus;
import ng.itlearning.gateway.payment.PaymentView;
import ng.itlearning.gateway.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_api.write')")
    ResponseEntity<PaymentView> create(@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                       @Valid @RequestBody CreatePayment request) {
        PaymentView view = service.create(CurrentUser.subject(), idempotencyKey, request);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + view.reference())).body(view);
    }

    @GetMapping("/{reference}")
    @PreAuthorize("hasAuthority('SCOPE_api.read') or hasRole('OPS')")
    PaymentView get(@PathVariable String reference, Authentication auth) {
        return service.get(merchantScope(auth), reference);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_api.read') or hasRole('OPS')")
    Page list(@RequestParam(required = false) PaymentStatus status, @RequestParam(required = false) Instant from,
              @RequestParam(required = false) Instant to, @RequestParam(required = false) String cursor,
              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit, Authentication auth) {
        List<Payment> rows = service.page(merchantScope(auth), status, from, to, cursor, limit + 1);
        boolean more = rows.size() > limit;
        List<Payment> page = more ? rows.subList(0, limit) : rows;
        String next = more ? new Cursor(page.getLast().createdAt(), page.getLast().id()).encode() : null;
        return new Page(page.stream().map(p -> new Summary(p.reference(), p.amountMinor(), p.currency(),
                p.status().name(), p.provider(), p.createdAt())).toList(), next);
    }

    @PostMapping("/{reference}/verify")
    @PreAuthorize("hasAuthority('SCOPE_api.write')")
    PaymentView verify(@PathVariable String reference) {
        return service.verify(CurrentUser.subject(), reference);
    }

    @PostMapping("/{reference}/refunds")
    @PreAuthorize("hasAuthority('SCOPE_api.write')")
    PaymentView refund(@PathVariable String reference, @Valid @RequestBody RefundRequest request) {
        return service.refund(CurrentUser.subject(), reference, request.amount());
    }

    /** Operations staff may read every merchant's payments; everyone else only their own. */
    private static String merchantScope(Authentication auth) {
        boolean ops = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OPS"));
        return ops ? null : CurrentUser.subject();
    }

    public record RefundRequest(@NotNull @Min(1) Long amount) {
    }

    public record Summary(String reference, long amount, String currency, String status, String provider, Instant createdAt) {
    }

    public record Page(List<Summary> items, String nextCursor) {
    }
}
