package ng.itlearning.lab.payments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class PaymentController {

    private final ProviderClient provider;
    final Map<String, Payment> payments = new ConcurrentHashMap<>();

    public PaymentController(ProviderClient provider) {
        this.provider = provider;
    }

    public record CreatePayment(@Min(100) long amount, @Email String email) {
    }

    public record Payment(String reference, long amount, String authorizationUrl, String status) {
    }

    @PostMapping("/payments")
    ResponseEntity<Payment> create(@RequestHeader(name = "Idempotency-Key", required = false) String key,
                                   @Valid @RequestBody CreatePayment body) {
        String reference = "pay_" + UUID.randomUUID();
        String url = provider.initialise(reference, body.amount(), body.email());
        Payment p = new Payment(reference, body.amount(), url, "PENDING");
        payments.put(reference, p);
        return ResponseEntity.status(HttpStatus.CREATED).body(p);
    }

    @ExceptionHandler(ProviderClient.ProviderException.class)
    ProblemDetail providerFailed(ProviderClient.ProviderException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
        problem.setTitle("Payment provider error");
        return problem;
    }
}
