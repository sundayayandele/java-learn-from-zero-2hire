package ng.itlearning.fxrates.rates;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Cached lookups. Kept in a separate bean from RateService so @Cacheable goes through the proxy (topic 4.3). */
@Repository
public class RateRepository {

    public record CurrencyCoverage(String currency, LocalDate firstDate, LocalDate lastDate) { }

    public record DatedRate(LocalDate date, BigDecimal rate) { }

    private final JdbcClient jdbc;

    public RateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The latest ECB publication date on or before the given date, if any. */
    @Cacheable(cacheNames = "publicationDates")
    public Optional<LocalDate> publicationOnOrBefore(LocalDate date) {
        return jdbc.sql("SELECT max(rate_date) FROM fx_rate WHERE rate_date <= :d")
                .param("d", date).query(LocalDate.class).optional();
    }

    public Optional<LocalDate> latestPublication() {
        return jdbc.sql("SELECT max(rate_date) FROM fx_rate").query(LocalDate.class).optional();
    }

    @Cacheable(cacheNames = "ratesPerEur")
    public Optional<BigDecimal> ratePerEur(String currency, LocalDate date) {
        return jdbc.sql("SELECT rate_per_eur FROM fx_rate WHERE currency = :c AND rate_date = :d")
                .param("c", currency).param("d", date).query(BigDecimal.class).optional();
    }

    public List<DatedRate> series(String currency, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT rate_date, rate_per_eur FROM fx_rate
                        WHERE currency = :c AND rate_date BETWEEN :from AND :to
                        ORDER BY rate_date
                        """)
                .param("c", currency).param("from", from).param("to", to)
                .query((rs, n) -> new DatedRate(rs.getObject("rate_date", LocalDate.class), rs.getBigDecimal("rate_per_eur")))
                .list();
    }

    public List<CurrencyCoverage> coverage() {
        return jdbc.sql("""
                        SELECT currency, min(rate_date) AS first_date, max(rate_date) AS last_date
                        FROM fx_rate GROUP BY currency ORDER BY currency
                        """)
                .query((rs, n) -> new CurrencyCoverage(rs.getString("currency"),
                        rs.getObject("first_date", LocalDate.class), rs.getObject("last_date", LocalDate.class)))
                .list();
    }
}
