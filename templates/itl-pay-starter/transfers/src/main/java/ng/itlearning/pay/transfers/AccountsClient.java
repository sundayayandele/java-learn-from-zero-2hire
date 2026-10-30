package ng.itlearning.pay.transfers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Calls accounts through the topic 10.3 circuit breaker: a 4xx is an answer, anything else is an outage. */
@Component
class AccountsClient {

    static class Rejected extends RuntimeException {
        final ProblemDetail problem;
        Rejected(ProblemDetail problem) { super(problem.getDetail()); this.problem = problem; }
    }

    static class Unavailable extends RuntimeException {
        Unavailable(Throwable cause) { super("Accounts is unavailable", cause); }
    }

    private final RestClient accounts;
    private final CircuitBreakerFactory<?, ?> breakers;

    AccountsClient(RestClient.Builder builder, CircuitBreakerFactory<?, ?> breakers,
                   @Value("${accounts.url}") String accountsUrl) {
        var timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofMillis(500));
        timeouts.setReadTimeout(Duration.ofSeconds(2));
        this.accounts = builder.baseUrl(accountsUrl).requestFactory(timeouts).build();
        this.breakers = breakers;
    }

    void reserve(String account, String reference, BigDecimal amount) {
        call(() -> accounts.post().uri("/accounts/{id}/holds", account)
                .body(Map.of("reference", reference, "amount", amount))
                .retrieve().toBodilessEntity());
    }

    /** The compensation for reserve: idempotent in accounts, so safe to repeat. */
    void release(String account, String reference) {
        call(() -> accounts.delete().uri("/accounts/{id}/holds/{reference}", account, reference)
                .retrieve().toBodilessEntity());
    }

    private void call(Runnable request) {
        try {
            breakers.create("accounts").run(
                    () -> { request.run(); return null; },
                    failure -> {
                        if (failure instanceof HttpClientErrorException e) {
                            throw e;                                // a business rejection is not an outage
                        }
                        throw new Unavailable(failure);
                    });
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 422) {
                throw new Rejected(e.getResponseBodyAs(ProblemDetail.class));
            }
            throw e;
        }
    }
}
