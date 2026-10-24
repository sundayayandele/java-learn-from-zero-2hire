package ng.itlearning.practice.arrays;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LongestActiveStreakTest {

    static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    @Test
    void findsTheLongestRunAcrossMonthEnds() {
        assertThat(LongestActiveStreak.longest(List.of(d("2026-03-02"), d("2026-02-28"), d("2026-03-01"),
                d("2026-03-10"), d("2026-02-27"), d("2026-03-01")))).isEqualTo(4);
    }

    @Test
    void emptyAndSingle() {
        assertThat(LongestActiveStreak.longest(List.of())).isZero();
        assertThat(LongestActiveStreak.longest(List.of(d("2026-01-01")))).isEqualTo(1);
    }

    @Test
    void largeInput() {
        List<LocalDate> days = new ArrayList<>();
        LocalDate start = d("2000-01-01");
        for (int i = 0; i < 100_000; i++) {
            days.add(start.plusDays(i * 2L));
        }
        days.add(start.plusDays(1));
        assertThat(LongestActiveStreak.longest(days)).isEqualTo(3);
    }
}
