package ng.itlearning.pay.rates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Quotes: how many naira a customer pays for an amount of foreign currency, valid for 30 seconds (topics 14.1, 14.2).
 * Every step is non-blocking: the provider call, the database read and the insert.
 */
@RestController
class QuoteController {

    record QuoteRequest(String pair, BigDecimal amount) { }
    record Quote(UUID id, String pair, BigDecimal amount, BigDecimal rate, BigDecimal total, Instant expiresAt) { }
    record ProviderRate(String pair, BigDecimal ask) { }

    private final DatabaseClient db;
    private final RateController rates;
    private final WebClient provider;
    private final long providerDelayMs;

    QuoteController(DatabaseClient db, RateController rates, WebClient.Builder web,
                    @Value("${provider.url}") String providerUrl, @Value("${provider.delay-ms}") long providerDelayMs) {
        this.db = db;
        this.rates = rates;
        this.provider = web.baseUrl(providerUrl).build();
        this.providerDelayMs = providerDelayMs;
    }

    @PostMapping("/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional                                         // a reactive transaction: R2DBC's, bound to this subscription
    Mono<Quote> quote(@RequestBody QuoteRequest req) {
        if (req.amount() == null || req.amount().signum() <= 0) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive"));
        }
        String[] parts = req.pair().split("-");
        return rates.one(parts[0], parts[1])
                .map(r -> new Quote(UUID.randomUUID(), r.pair(), req.amount(), r.ask(),
                        req.amount().multiply(r.ask()).setScale(2, RoundingMode.HALF_EVEN), Instant.now().plusSeconds(30)))
                .flatMap(q -> db.sql("""
                                INSERT INTO quote (id, pair, amount, rate, total, expires_at)
                                VALUES (:id, :pair, :amount, :rate, :total, :expiresAt)""")
                        .bind("id", q.id()).bind("pair", q.pair()).bind("amount", q.amount())
                        .bind("rate", q.rate()).bind("total", q.total()).bind("expiresAt", q.expiresAt())
                        .fetch().rowsUpdated()
                        .thenReturn(q));
    }

    /**
     * The benchmark endpoint (topic 14.3): ask the provider (which takes provider.delay-ms), read our own rate,
     * and answer. rates-mvc does exactly the same work with blocking calls on virtual threads.
     */
    @GetMapping("/bench/quote/{base}-{quote}")
    Mono<Quote> benchQuote(@PathVariable String base, @PathVariable String quote) {
        Mono<ProviderRate> fromProvider = provider.get()
                .uri("/provider/{base}-{quote}?delayMs={d}", base, quote, providerDelayMs)
                .retrieve().bodyToMono(ProviderRate.class)
                .timeout(Duration.ofSeconds(2));
        return fromProvider.zipWith(rates.one(base, quote))          // both at once; neither blocks a thread
                .map(t -> new Quote(UUID.randomUUID(), t.getT2().pair(), BigDecimal.ONE,
                        t.getT1().ask().max(t.getT2().ask()), t.getT1().ask().max(t.getT2().ask()), Instant.now().plusSeconds(30)));
    }

    /**
     * Plays the external FX provider: answers from memory after a delay, without holding a thread while it waits.
     * No database read, so in the benchmark the provider is never the bottleneck (topic 14.3).
     */
    @GetMapping("/provider/{base}-{quote}")
    Mono<ProviderRate> provider(@PathVariable String base, @PathVariable String quote, @RequestParam(defaultValue = "0") long delayMs) {
        return Mono.just(new ProviderRate(base + "-" + quote, PROVIDER_ASK))
                .delayElement(Duration.ofMillis(delayMs));
    }

    private static final BigDecimal PROVIDER_ASK = new BigDecimal("1540.0000");
}
