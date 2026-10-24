package ng.itlearning.openbanking.audit;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Append-only record of every data access decision (ADR 0004). Writes run in their own transaction
 * so a denial is recorded even when the request itself fails.
 */
@Component
public class AccessLog {

    private final JdbcClient jdbc;
    private final TransactionTemplate own;
    private final Clock clock;

    public AccessLog(JdbcClient jdbc, PlatformTransactionManager tm, Clock clock) {
        this.jdbc = jdbc;
        this.own = new TransactionTemplate(tm);
        this.own.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public void record(String tppId, String consentId, Long customerId, String endpoint, String accountId,
                       boolean allowed, String reason) {
        own.executeWithoutResult(s -> jdbc.sql("""
                insert into access_log (at, tpp_id, consent_id, customer_id, endpoint, account_id, outcome, reason)
                values (?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(Timestamp.from(clock.instant()), tppId, consentId, customerId, endpoint, accountId,
                        allowed ? "ALLOWED" : "DENIED", reason)
                .update());
    }

    public List<Entry> search(String consentId, Instant from, Instant to, Long beforeId, int limit) {
        StringBuilder sql = new StringBuilder("select * from access_log where true");
        var params = new java.util.ArrayList<Object>();
        if (consentId != null) {
            sql.append(" and consent_id = ?");
            params.add(consentId);
        }
        if (from != null) {
            sql.append(" and at >= ?");
            params.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" and at < ?");
            params.add(Timestamp.from(to));
        }
        if (beforeId != null) {
            sql.append(" and id < ?");
            params.add(beforeId);
        }
        sql.append(" order by id desc limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params).query((rs, n) -> new Entry(rs.getLong("id"),
                rs.getTimestamp("at").toInstant(), rs.getString("tpp_id"), rs.getString("consent_id"),
                (Long) rs.getObject("customer_id"), rs.getString("endpoint"), rs.getString("account_id"),
                rs.getString("outcome"), rs.getString("reason"))).list();
    }

    public record Entry(long id, Instant at, String tppId, String consentId, Long customerId, String endpoint,
                        String accountId, String outcome, String reason) {
    }
}
