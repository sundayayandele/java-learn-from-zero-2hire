package ng.itlearning.pay.transfers;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;

/** The cache is an optimisation: with Redis gone, status reads still work, from the database (topic 13.4). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RedisDownTests {

    static final HttpServer fakeAccounts = startFakeAccounts();   // a context of its own, so stopping Redis affects no other test

    static HttpServer startFakeAccounts() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/accounts", exchange -> {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, 2);
                exchange.getResponseBody().write("{}".getBytes());
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void accountsUrl(DynamicPropertyRegistry registry) {
        registry.add("accounts.url", () -> "http://localhost:" + fakeAccounts.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeAccounts() {
        fakeAccounts.stop(0);
    }

    @LocalServerPort int port;
    @Autowired GenericContainer<?> redis;

    @Test
    void statusIsStillAnsweredFromTheDatabaseWhenRedisIsDown() {
        RestClient http = RestClient.create("http://localhost:" + port);
        http.post().uri("/transfers").header("Content-Type", "application/json")
                .body("{\"reference\":\"TRF-NOREDIS\",\"from\":\"A1\",\"to\":\"A2\",\"amount\":10.00}")
                .retrieve().toBodilessEntity();
        assertThat(http.get().uri("/transfers/TRF-NOREDIS").retrieve().body(String.class)).contains("RESERVED");

        redis.stop();

        long start = System.nanoTime();
        assertThat(http.get().uri("/transfers/TRF-NOREDIS").retrieve().body(String.class)).contains("RESERVED");
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000);   // fails fast to the database, no 60 s wait
    }
}
