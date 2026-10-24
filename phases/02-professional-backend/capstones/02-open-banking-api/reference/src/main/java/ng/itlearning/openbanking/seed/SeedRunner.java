package ng.itlearning.openbanking.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;

/**
 * Run once with {@code SPRING_PROFILES_ACTIVE=seed}: creates alice and bob (matching the Keycloak dev users)
 * plus 200 synthetic customers with a year of transactions (about 100,000 rows).
 */
@Component
@Profile("seed")
class SeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final DemoData data;
    private final JdbcClient jdbc;

    SeedRunner(DemoData data, JdbcClient jdbc) {
        this.data = data;
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        if (jdbc.sql("select count(*) from customer").query(Integer.class).single() > 0) {
            log.info("Seed skipped: customers already exist");
            return;
        }
        data.customer("alice", "Alice Adeyemi", List.of("CURRENT", "SAVINGS", "DOMICILIARY"), 250, 1);
        data.customer("bob", "Bob Okafor", List.of("CURRENT", "SAVINGS"), 250, 2);
        Random random = new Random(42);
        for (int i = 1; i <= 200; i++) {
            List<String> types = random.nextBoolean() ? List.of("CURRENT", "SAVINGS") : List.of("CURRENT", "SAVINGS", "DOMICILIARY");
            data.customer("customer" + i, DemoData.randomName(random), types, 180, 100 + i);
        }
        log.info("Seeded 202 customers");
    }
}
