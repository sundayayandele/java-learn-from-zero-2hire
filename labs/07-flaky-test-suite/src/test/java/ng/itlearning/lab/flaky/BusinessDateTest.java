package ng.itlearning.lab.flaky;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessDateTest {

    @Test
    void morningInLagosIsTheSameBusinessDay() {
        assertThat(BusinessDate.of(Instant.parse("2026-10-01T10:30:00Z"))).isEqualTo(LocalDate.parse("2026-10-01"));
    }

    @Test
    void lateEveningUtcIsAlreadyTomorrowInLagos() {
        assertThat(BusinessDate.of(Instant.parse("2026-10-01T23:30:00Z"))).isEqualTo(LocalDate.parse("2026-10-02"));
    }
}
