package ng.itlearning.fxrates.imports;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Import history, written in its own transactions so failures are recorded even when the import rolls back. */
@Repository
public class ImportRunLog {

    public record ImportRun(long id, String source, OffsetDateTime startedAt, OffsetDateTime finishedAt,
                            Integer rowsRead, Integer rowsInserted, String status, String error) { }

    private final JdbcClient jdbc;
    private final Clock clock;

    public ImportRunLog(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long start(String source) {
        return jdbc.sql("INSERT INTO import_run (source, started_at, status) VALUES (:s, :t, 'RUNNING') RETURNING id")
                .param("s", source).param("t", now()).query(Long.class).single();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(long id, int rowsRead, int rowsInserted) {
        jdbc.sql("UPDATE import_run SET status = 'SUCCEEDED', finished_at = :t, rows_read = :r, rows_inserted = :i WHERE id = :id")
                .param("t", now()).param("r", rowsRead).param("i", rowsInserted).param("id", id).update();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(long id, String error) {
        String message = error == null ? "unknown error" : error.substring(0, Math.min(error.length(), 500));
        jdbc.sql("UPDATE import_run SET status = 'FAILED', finished_at = :t, error = :e WHERE id = :id")
                .param("t", now()).param("e", message).param("id", id).update();
    }

    public List<ImportRun> recent(int limit) {
        return jdbc.sql("""
                        SELECT id, source, started_at, finished_at, rows_read, rows_inserted, status, error
                        FROM import_run ORDER BY id DESC LIMIT :limit
                        """)
                .param("limit", limit)
                .query((rs, n) -> new ImportRun(rs.getLong("id"), rs.getString("source"),
                        rs.getObject("started_at", OffsetDateTime.class), rs.getObject("finished_at", OffsetDateTime.class),
                        (Integer) rs.getObject("rows_read"), (Integer) rs.getObject("rows_inserted"),
                        rs.getString("status"), rs.getString("error")))
                .list();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
