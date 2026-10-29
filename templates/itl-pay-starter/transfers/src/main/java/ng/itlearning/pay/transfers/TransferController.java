package ng.itlearning.pay.transfers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/transfers")
class TransferController {

    record TransferRequest(String reference, String from, String to, BigDecimal amount) { }

    private final RestClient accounts;
    private final CircuitBreakerFactory<?, ?> breakers;

    TransferController(RestClient.Builder builder, CircuitBreakerFactory<?, ?> breakers,
                       @Value("${accounts.url}") String accountsUrl) {
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofMillis(500));
        timeouts.setReadTimeout(Duration.ofSeconds(2));
        this.accounts = builder.baseUrl(accountsUrl).requestFactory(timeouts).build();
        this.breakers = breakers;
    }

    @PostMapping
    ResponseEntity<?> transfer(@RequestBody TransferRequest req) {
        try {
            return breakers.create("accounts").run(
                    () -> {
                        accounts.post().uri("/accounts/{id}/holds", req.from())
                                .body(Map.of("reference", req.reference(), "amount", req.amount()))
                                .retrieve().toBodilessEntity();
                        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("reference", req.reference(), "status", "PENDING"));
                    },
                    failure -> {
                        if (failure instanceof HttpClientErrorException e && e.getStatusCode().value() == 422) {
                            throw e;                                // a business rejection is not an outage
                        }
                        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                                "Accounts is unavailable; the transfer was not started. Try again shortly.");
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(pd);
                    });
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAs(ProblemDetail.class));
        }
    }
}
