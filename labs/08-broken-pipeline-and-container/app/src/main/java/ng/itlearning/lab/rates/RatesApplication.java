package ng.itlearning.lab.rates;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@SpringBootApplication
@RestController
public class RatesApplication {

    private static final Map<String, String> RATES = Map.of("USD", "1532.40", "GBP", "2041.10", "EUR", "1764.85");

    public static void main(String[] args) {
        SpringApplication.run(RatesApplication.class, args);
    }

    @GetMapping("/api/rates/{currency}")
    Map<String, String> rate(@PathVariable String currency) {
        return Map.of("currency", currency, "ngn", RATES.getOrDefault(currency, "unknown"));
    }
}
