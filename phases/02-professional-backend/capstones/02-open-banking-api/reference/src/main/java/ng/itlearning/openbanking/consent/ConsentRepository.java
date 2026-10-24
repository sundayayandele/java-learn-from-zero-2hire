package ng.itlearning.openbanking.consent;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class ConsentRepository {

    private static final RowMapper<Consent> ROW = (rs, n) -> new Consent(rs.getString("id"), rs.getString("tpp_id"),
            (Long) rs.getObject("customer_id"), Consent.parse(rs.getString("permissions")),
            ConsentStatus.valueOf(rs.getString("status")), rs.getTimestamp("expires_at").toInstant(),
            instant(rs.getTimestamp("tx_from")), instant(rs.getTimestamp("tx_to")), rs.getTimestamp("created_at").toInstant());

    private final JdbcClient jdbc;

    public ConsentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Consent c) {
        jdbc.sql("""
                insert into consent (id, tpp_id, permissions, status, expires_at, tx_from, tx_to, created_at, status_updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(c.id(), c.tppId(), c.permissions().stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
                        c.storedStatus().name(), ts(c.expiresAt()), ts(c.txFrom()), ts(c.txTo()), ts(c.createdAt()), ts(c.createdAt()))
                .update();
    }

    public Optional<Consent> find(String id) {
        return jdbc.sql("select * from consent where id = ?").param(id).query(ROW).optional();
    }

    public Optional<Consent> lock(String id) {
        return jdbc.sql("select * from consent where id = ? for update").param(id).query(ROW).optional();
    }

    public void updateStatus(String id, ConsentStatus status, Long customerId, Instant now) {
        jdbc.sql("update consent set status = ?, customer_id = coalesce(?, customer_id), status_updated_at = ? where id = ?")
                .params(status.name(), customerId, ts(now), id).update();
    }

    public void addAccounts(String consentId, List<String> accountIds) {
        for (String accountId : accountIds) {
            jdbc.sql("insert into consent_account (consent_id, account_id) values (?, ?)").params(consentId, accountId).update();
        }
    }

    public Set<String> accounts(String consentId) {
        return Set.copyOf(jdbc.sql("select account_id from consent_account where consent_id = ?")
                .param(consentId).query(String.class).list());
    }

    public List<Consent> forCustomer(long customerId) {
        return jdbc.sql("select * from consent where customer_id = ? order by created_at desc").param(customerId).query(ROW).list();
    }

    /** Counts this call atomically and returns the new total for the day. */
    public int countCall(String consentId, String accountId, String endpoint, java.time.LocalDate day) {
        return jdbc.sql("""
                insert into access_counter (consent_id, account_id, endpoint, day, calls) values (?, ?, ?, ?, 1)
                on conflict (consent_id, account_id, endpoint, day) do update set calls = access_counter.calls + 1
                returning calls""")
                .params(consentId, accountId, endpoint, day).query(Integer.class).single();
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
