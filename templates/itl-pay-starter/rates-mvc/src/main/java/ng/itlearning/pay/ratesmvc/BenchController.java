package ng.itlearning.pay.ratesmvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * The same work as the reactive service's /bench/quote, written as ordinary blocking code (topic 14.3).
 * Each request runs on a virtual thread, so waiting for the provider parks the virtual thread, not an OS thread.
 */
@RestController
class BenchController {

    record ProviderRate(String pair, BigDecimal ask) { }
    record Quote(UUID id, String pair, BigDecimal amount, BigDecimal rate, BigDecimal total, Instant expiresAt) { }

    private final JdbcClient db;
    private final RestClient provider;
    private final long providerDelayMs;

    BenchController(JdbcClient db, RestClient.Builder web,
                    @Value("${provider.url}") String providerUrl, @Value("${provider.delay-ms}") long providerDelayMs) {
        var http = new JdkClientHttpRequestFactory();
        http.setReadTimeout(Duration.ofSeconds(2));
        this.db = db;
        this.provider = web.baseUrl(providerUrl).requestFactory(http).build();
        this.providerDelayMs = providerDelayMs;
    }

    @GetMapping("/bench/quote/{base}-{quote}")
    Quote benchQuote(@PathVariable String base, @PathVariable String quote) {
        ProviderRate fromProvider = provider.get()
                .uri("/provider/{base}-{quote}?delayMs={d}", base, quote, providerDelayMs)
                .retrieve().body(ProviderRate.class);                         // blocks this virtual thread for ~100 ms
        BigDecimal ours = db.sql("SELECT ask FROM rate WHERE pair = ?")
                .param(base + "-" + quote).query(BigDecimal.class).single();
        BigDecimal rate = fromProvider.ask().max(ours);
        return new Quote(UUID.randomUUID(), base + "-" + quote, BigDecimal.ONE, rate, rate, Instant.now().plusSeconds(30));
    }
}
