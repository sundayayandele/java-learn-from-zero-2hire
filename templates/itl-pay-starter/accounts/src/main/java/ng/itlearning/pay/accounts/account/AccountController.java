package ng.itlearning.pay.accounts.account;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
class AccountController {

    record Balance(String accountId, BigDecimal available) { }
    record HoldRequest(String reference, BigDecimal amount) { }

    private final Map<String, BigDecimal> available = new ConcurrentHashMap<>(Map.of("A1", new BigDecimal("50000.00"), "A2", new BigDecimal("1000.00")));
    private final Map<String, BigDecimal> holds = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;

    AccountController(ApplicationEventPublisher events) { this.events = events; }

    @GetMapping("/{id}")
    Balance balance(@PathVariable String id, @RequestParam(defaultValue = "0") long delayMs) throws InterruptedException {
        Thread.sleep(delayMs);   // lets the lesson simulate a slow dependency
        return new Balance(id, available.getOrDefault(id, BigDecimal.ZERO));
    }

    @PostMapping("/{id}/holds")
    synchronized ResponseEntity<?> hold(@PathVariable String id, @RequestBody HoldRequest req) {
        String key = id + ":" + req.reference();
        if (holds.containsKey(key)) {                               // idempotent: same reference, same result
            return ResponseEntity.ok(Map.of("reference", req.reference(), "status", "RESERVED"));
        }
        BigDecimal balance = available.getOrDefault(id, BigDecimal.ZERO);
        if (balance.compareTo(req.amount()) < 0) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "Insufficient funds");
            return ResponseEntity.unprocessableContent().body(pd);
        }
        available.put(id, balance.subtract(req.amount()));
        holds.put(key, req.amount());
        events.publishEvent(new FundsReserved(id, req.reference(), req.amount()));
        return ResponseEntity.created(URI.create("/accounts/" + id + "/holds/" + req.reference()))
                .body(Map.of("reference", req.reference(), "status", "RESERVED"));
    }
}
