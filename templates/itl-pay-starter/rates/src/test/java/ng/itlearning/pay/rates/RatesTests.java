package ng.itlearning.pay.rates;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"rates.tick-ms=100", "provider.delay-ms=50"})
@AutoConfigureWebTestClient
@Import(RatesTests.Containers.class)
class RatesTests {

    /** A container bean lives and dies with the Spring context, so the rate feed never outlives its database. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection                                 // configures both R2DBC (the app) and JDBC (Flyway)
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    /** A stand-in for the external FX provider: answers after 50 ms. */
    static final HttpServer fakeProvider = startFakeProvider();

    static HttpServer startFakeProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/provider", exchange -> {
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                byte[] body = "{\"pair\":\"EUR-NGN\",\"ask\":1662.0}".getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry registry) {
        registry.add("provider.url", () -> "http://localhost:" + fakeProvider.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeProvider() {
        fakeProvider.stop(0);
    }

    @Autowired WebTestClient http;
    @Autowired RateFeed feed;
    @Autowired DatabaseClient db;

    @Test
    void returnsTheCurrentRate() {
        http.get().uri("/rates/USD-NGN").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.pair").isEqualTo("USD-NGN");
        http.get().uri("/rates/XXX-NGN").exchange().expectStatus().isNotFound();
    }

    @Test
    void streamsTicksAsServerSentEvents() {
        var events = http.get().uri("/rates/stream?pair=GBP-NGN").accept(MediaType.TEXT_EVENT_STREAM).exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<Rate>>() { })
                .getResponseBody();
        StepVerifier.create(events.filter(e -> e.data() != null).take(3))
                .thenConsumeWhile(e -> e.data().pair().equals("GBP-NGN") && "rate".equals(e.event()))
                .expectComplete()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void aNewSubscriberGetsFreshTicksNotOnesFromBeforeItJoined() throws InterruptedException {
        Thread.sleep(1500);                                   // ticks are published before anyone listens
        java.time.Instant joined = java.time.Instant.now();
        StepVerifier.create(feed.ticks().take(3))
                .thenConsumeWhile(r -> !r.updatedAt().isBefore(joined.minusMillis(200)))
                .expectComplete()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void savesAQuoteInsideAReactiveTransaction() {
        http.post().uri("/quotes").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"pair\":\"USD-NGN\",\"amount\":100.00}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody().jsonPath("$.total").isNumber();
        StepVerifier.create(db.sql("SELECT count(*) AS n FROM quote").map(row -> row.get("n", Long.class)).one())
                .assertNext(n -> assertThat(n).isGreaterThanOrEqualTo(1))
                .verifyComplete();
    }

    @Test
    void benchmarkQuoteCallsTheProviderWithoutBlocking() {
        http.get().uri("/bench/quote/EUR-NGN").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.pair").isEqualTo("EUR-NGN");
    }
}
