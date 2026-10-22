package ng.itlearning.ledger.statement;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.List;

/** Read-side queries in plain SQL: statements, trial balance and consistency checks (topics 3.1 and 3.5). */
@Repository
public class LedgerQueries {

    public record StatementLine(Instant postedAt, String reference, String type, String narration,
                                String direction, BigDecimal amount, BigDecimal runningBalance) { }

    public record TrialBalanceLine(String number, String holderName, String kind, String currency,
                                   BigDecimal debits, BigDecimal credits) { }

    public record BalanceDifference(String number, BigDecimal storedBalance, BigDecimal postingsBalance) { }

    private final JdbcClient jdbc;

    public LedgerQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Debits from an account in TRANSFER journals during one business day: amount plus fees. */
    public BigDecimal outgoingTransfersOn(long accountId, LocalDate day, ZoneId zone) {
        return jdbc.sql("""
                        SELECT coalesce(sum(p.amount), 0)
                        FROM posting p JOIN journal j ON j.id = p.journal_id
                        WHERE p.account_id = :account AND p.direction = 'D' AND j.type = 'TRANSFER'
                          AND p.posted_at >= :start AND p.posted_at < :end
                        """)
                .param("account", accountId)
                .param("start", ts(day.atStartOfDay(zone).toInstant()))
                .param("end", ts(day.plusDays(1).atStartOfDay(zone).toInstant()))
                .query(BigDecimal.class)
                .single();
    }

    /** Account numbers touched by a journal, without loading entities (used to lock before loading). */
    public List<String> accountNumbersOf(String reference) {
        return jdbc.sql("""
                        SELECT DISTINCT a.number
                        FROM journal j JOIN posting p ON p.journal_id = j.id JOIN account a ON a.id = p.account_id
                        WHERE j.reference = :reference
                        """)
                .param("reference", reference)
                .query(String.class)
                .list();
    }

    /** Balance from postings before an instant, signed from the account's point of view. */
    public BigDecimal balanceBefore(long accountId, Instant before) {
        return jdbc.sql("""
                        SELECT coalesce(sum(CASE WHEN p.direction = a.normal_side THEN p.amount ELSE -p.amount END), 0)
                        FROM account a JOIN posting p ON p.account_id = a.id
                        WHERE a.id = :account AND p.posted_at < :before
                        """)
                .param("account", accountId)
                .param("before", ts(before))
                .query(BigDecimal.class)
                .single();
    }

    /** One page of a statement, with a running balance computed over the whole period by a window function. */
    public List<StatementLine> statementPage(long accountId, Instant from, Instant to, BigDecimal opening, int page, int size) {
        return jdbc.sql("""
                        WITH lines AS (
                            SELECT p.id, p.posted_at, j.reference, j.type, j.narration, p.direction, p.amount,
                                   sum(CASE WHEN p.direction = a.normal_side THEN p.amount ELSE -p.amount END)
                                       OVER (ORDER BY p.posted_at, p.id) AS movement
                            FROM posting p
                            JOIN journal j ON j.id = p.journal_id
                            JOIN account a ON a.id = p.account_id
                            WHERE p.account_id = :account AND p.posted_at >= :from AND p.posted_at < :to
                        )
                        SELECT posted_at, reference, type, narration, direction, amount, :opening + movement AS running_balance
                        FROM lines
                        ORDER BY posted_at, id
                        LIMIT :size OFFSET :offset
                        """)
                .param("account", accountId)
                .param("from", ts(from))
                .param("to", ts(to))
                .param("opening", opening)
                .param("size", size)
                .param("offset", page * size)
                .query((rs, n) -> new StatementLine(rs.getObject("posted_at", OffsetDateTime.class).toInstant(), rs.getString("reference"),
                        rs.getString("type"), rs.getString("narration"), rs.getString("direction"),
                        rs.getBigDecimal("amount"), rs.getBigDecimal("running_balance")))
                .list();
    }

    public long statementLineCount(long accountId, Instant from, Instant to) {
        return jdbc.sql("SELECT count(*) FROM posting WHERE account_id = :account AND posted_at >= :from AND posted_at < :to")
                .param("account", accountId).param("from", ts(from)).param("to", ts(to))
                .query(Long.class).single();
    }

    public List<TrialBalanceLine> trialBalance() {
        return jdbc.sql("""
                        SELECT a.number, a.holder_name, a.kind, a.currency,
                               coalesce(sum(p.amount) FILTER (WHERE p.direction = 'D'), 0) AS debits,
                               coalesce(sum(p.amount) FILTER (WHERE p.direction = 'C'), 0) AS credits
                        FROM account a LEFT JOIN posting p ON p.account_id = a.id
                        GROUP BY a.id
                        ORDER BY a.number
                        """)
                .query((rs, n) -> new TrialBalanceLine(rs.getString("number"), rs.getString("holder_name"),
                        rs.getString("kind"), rs.getString("currency"), rs.getBigDecimal("debits"), rs.getBigDecimal("credits")))
                .list();
    }

    public List<BalanceDifference> balanceDifferences() {
        return jdbc.sql("""
                        SELECT a.number, a.balance,
                               coalesce(sum(CASE WHEN p.direction = a.normal_side THEN p.amount ELSE -p.amount END), 0) AS computed
                        FROM account a LEFT JOIN posting p ON p.account_id = a.id
                        GROUP BY a.id
                        HAVING a.balance <> coalesce(sum(CASE WHEN p.direction = a.normal_side THEN p.amount ELSE -p.amount END), 0)
                        """)
                .query((rs, n) -> new BalanceDifference(rs.getString("number"), rs.getBigDecimal("balance"), rs.getBigDecimal("computed")))
                .list();
    }

    /** Bind instants as UTC OffsetDateTime so the driver sends a timestamptz, independent of the JVM time zone. */
    private static OffsetDateTime ts(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
