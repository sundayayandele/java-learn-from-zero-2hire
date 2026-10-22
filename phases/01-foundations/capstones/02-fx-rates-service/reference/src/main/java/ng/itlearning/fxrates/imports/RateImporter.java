package ng.itlearning.fxrates.imports;

import io.micrometer.core.instrument.MeterRegistry;
import ng.itlearning.fxrates.imports.EcbCsvParser.EcbRate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Loads ECB rates idempotently (ON CONFLICT DO NOTHING) in JDBC batches, inside one transaction guarded by a
 * PostgreSQL advisory lock so that only one instance imports at a time.
 */
@Service
public class RateImporter {

    public record ImportResult(long runId, int linesRead, int rowsInserted) { }

    private static final long IMPORT_LOCK_KEY = 0x45_43_42L;      // "ECB"
    private static final int BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbc;
    private final ImportRunLog runs;
    private final MeterRegistry metrics;

    public RateImporter(JdbcTemplate jdbc, ImportRunLog runs, MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.runs = runs;
        this.metrics = metrics;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "ratesPerEur", allEntries = true),
            @CacheEvict(cacheNames = "publicationDates", allEntries = true)})
    public ImportResult importCsv(InputStream csv, String source) {
        Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, IMPORT_LOCK_KEY);
        if (!Boolean.TRUE.equals(locked)) {
            throw new ImportAlreadyRunningException();
        }
        long runId = runs.start(source);                       // separate transaction: visible even if this one fails
        try {
            List<EcbRate> batch = new ArrayList<>(BATCH_SIZE);
            int[] inserted = {0};
            int lines = EcbCsvParser.parse(csv, rate -> {
                batch.add(rate);
                if (batch.size() == BATCH_SIZE) {
                    inserted[0] += flush(batch, runId);
                }
            });
            inserted[0] += flush(batch, runId);
            runs.succeed(runId, lines, inserted[0]);
            metrics.counter("fx.import.rows.inserted").increment(inserted[0]);
            return new ImportResult(runId, lines, inserted[0]);
        } catch (RuntimeException e) {
            runs.fail(runId, e.getMessage());
            throw e;
        }
    }

    private int flush(List<EcbRate> batch, long runId) {
        if (batch.isEmpty()) {
            return 0;
        }
        int[] counts = jdbc.batchUpdate("""
                        INSERT INTO fx_rate (currency, rate_date, rate_per_eur, import_run_id)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (currency, rate_date) DO NOTHING
                        """, batch, batch.size(), (ps, r) -> {
                    ps.setString(1, r.currency());
                    ps.setDate(2, Date.valueOf(r.date()));
                    ps.setBigDecimal(3, r.ratePerEur());
                    ps.setLong(4, runId);
                })[0];
        batch.clear();
        return Arrays.stream(counts).map(c -> Math.max(c, 0)).sum();
    }
}
