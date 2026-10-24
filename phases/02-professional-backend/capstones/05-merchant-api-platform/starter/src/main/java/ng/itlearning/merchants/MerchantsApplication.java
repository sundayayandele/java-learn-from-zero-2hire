package ng.itlearning.merchants;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MerchantsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantsApplication.class, args);
    }

    /** Inject this instead of calling now() directly, so tests can fix the time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
