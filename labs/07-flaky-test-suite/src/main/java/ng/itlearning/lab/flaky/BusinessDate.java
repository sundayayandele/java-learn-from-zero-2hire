package ng.itlearning.lab.flaky;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** The bank's business date for a moment in time. The bank operates on Lagos time. */
public final class BusinessDate {

    private BusinessDate() {
    }

    public static LocalDate of(Instant moment) {
        return LocalDate.ofInstant(moment, ZoneId.systemDefault());
    }
}
