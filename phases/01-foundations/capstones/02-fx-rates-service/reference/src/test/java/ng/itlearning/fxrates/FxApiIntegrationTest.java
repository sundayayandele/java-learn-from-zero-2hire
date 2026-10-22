package ng.itlearning.fxrates;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "fx.import.schedule-enabled=false")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FxApiIntegrationTest.FixedClock.class})
class FxApiIntegrationTest {

    /** "Today" is 21 October 2026, one day after the newest rates in the sample. */
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired MeterRegistry metrics;

    @BeforeEach
    void loadSample() throws Exception {
        mvc.perform(post("/api/v1/admin/imports/csv").contentType("text/csv").content(sample()))
                .andExpect(status().isCreated());
    }

    @Test
    void reimportingTheSameFileInsertsNothing() throws Exception {
        mvc.perform(post("/api/v1/admin/imports/csv").contentType("text/csv").content(sample()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linesRead").value(5))
                .andExpect(jsonPath("$.rowsInserted").value(0));
        mvc.perform(get("/api/v1/admin/imports")).andExpect(jsonPath("$[0].status").value("SUCCEEDED"));
    }

    @Test
    void directRateIsTheEcbValue() throws Exception {
        mvc.perform(get("/api/v1/rates/EUR/USD").param("date", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(1.1215))
                .andExpect(jsonPath("$.derived").value(false))
                .andExpect(jsonPath("$.source").value("Source: ECB statistics."));
    }

    @Test
    void inverseAndCrossRatesAreDerivedAtTenDecimals() throws Exception {
        mvc.perform(get("/api/v1/rates/USD/EUR").param("date", "2026-10-20"))
                .andExpect(jsonPath("$.rate").value(0.8916629514))
                .andExpect(jsonPath("$.derived").value(true));
        mvc.perform(get("/api/v1/rates/usd/gbp").param("date", "2026-10-20"))         // codes are case-insensitive
                .andExpect(jsonPath("$.rate").value(0.7492465448));
    }

    @Test
    void weekendFallsBackToThePreviousPublication() throws Exception {
        mvc.perform(get("/api/v1/rates/EUR/USD").param("date", "2026-10-18"))       // Sunday
                .andExpect(jsonPath("$.requestedDate").value("2026-10-18"))
                .andExpect(jsonPath("$.rateDate").value("2026-10-16"))
                .andExpect(jsonPath("$.rate").value(1.1253));
    }

    @Test
    void defaultDateIsTodayInTheEcbTimeZone() throws Exception {
        mvc.perform(get("/api/v1/rates/EUR/USD"))
                .andExpect(jsonPath("$.requestedDate").value("2026-10-21"))
                .andExpect(jsonPath("$.rateDate").value("2026-10-20"));
    }

    @Test
    void conversionRoundsToTheTargetCurrencyMinorUnits() throws Exception {
        mvc.perform(get("/api/v1/conversions").param("from", "USD").param("to", "JPY")
                        .param("amount", "100.00").param("date", "2026-10-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.converted").value(15813));                       // yen have no minor unit
    }

    @Test
    void errorsAreProblemDetails() throws Exception {
        mvc.perform(get("/api/v1/rates/EUR/XYZ")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://problems.itlearning.ng/fx/currency-not-found"));
        mvc.perform(get("/api/v1/rates/EUR/NGN")).andExpect(status().isNotFound())     // real currency, no ECB rate
                .andExpect(jsonPath("$.type").value("https://problems.itlearning.ng/fx/rate-not-available"));
        mvc.perform(get("/api/v1/rates/EUR/USD").param("date", "2026-11-30")).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/rates/EUR/USD").param("date", "1998-12-31")).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/v1/rates/EUR/USD").param("date", "20-10-2026")).andExpect(status().isBadRequest());
    }

    @Test
    void historyReturnsOnePointPerPublication() throws Exception {
        mvc.perform(get("/api/v1/rates/EUR/USD/history").param("from", "2026-10-16").param("to", "2026-10-20"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].date").value("2026-10-16"));
    }

    @Test
    void repeatedLookupsAreServedFromTheCache() throws Exception {
        mvc.perform(get("/api/v1/rates/GBP/CHF").param("date", "2026-10-19"));
        double hitsBefore = hits();
        mvc.perform(get("/api/v1/rates/GBP/CHF").param("date", "2026-10-19"));
        assertThat(hits()).isGreaterThan(hitsBefore);
    }

    private double hits() {
        FunctionCounter counter = metrics.find("cache.gets").tags("cache", "ratesPerEur", "result", "hit").functionCounter();
        return counter == null ? 0 : counter.count();
    }

    private static byte[] sample() throws Exception {
        try (var in = FxApiIntegrationTest.class.getResourceAsStream("/ecb/eurofxref-hist-sample.csv")) {
            return in.readAllBytes();
        }
    }
}
