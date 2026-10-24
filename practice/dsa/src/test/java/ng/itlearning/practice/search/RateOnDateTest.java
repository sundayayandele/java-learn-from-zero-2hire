package ng.itlearning.practice.search;

import ng.itlearning.practice.search.RateOnDate.Publication;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateOnDateTest {

    static final List<Publication> RATES = List.of(
            new Publication(LocalDate.parse("2026-10-01"), "1.1520"),
            new Publication(LocalDate.parse("2026-10-02"), "1.1534"),
            new Publication(LocalDate.parse("2026-10-05"), "1.1501"));

    @Test
    void exactAndWeekendDays() {
        assertThat(RateOnDate.rateOn(RATES, LocalDate.parse("2026-10-02"))).contains("1.1534");
        assertThat(RateOnDate.rateOn(RATES, LocalDate.parse("2026-10-04"))).contains("1.1534");
        assertThat(RateOnDate.rateOn(RATES, LocalDate.parse("2026-12-31"))).contains("1.1501");
    }

    @Test
    void beforeFirstPublication() {
        assertThat(RateOnDate.rateOn(RATES, LocalDate.parse("2026-09-30"))).isEmpty();
        assertThat(RateOnDate.rateOn(List.of(), LocalDate.parse("2026-09-30"))).isEmpty();
    }
}
