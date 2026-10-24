package ng.itlearning.gateway.payment;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** SQL with JdbcClient: the queries are simple and we want explicit row locks and paging. */
@Repository
public class PaymentRepository {

    private static final String COLUMNS = "id, reference, merchant_id, amount_minor, currency, email, provider, status, "
            + "authorization_url, refunded_minor, created_at, updated_at";
    private static final RowMapper<Payment> ROW = (rs, n) -> new Payment(rs.getLong("id"), rs.getString("reference"),
            rs.getString("merchant_id"), rs.getLong("amount_minor"), rs.getString("currency"), rs.getString("email"),
            rs.getString("provider"), PaymentStatus.valueOf(rs.getString("status")), rs.getString("authorization_url"),
            rs.getLong("refunded_minor"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    private final JdbcClient jdbc;

    public PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String reference, String merchantId, long amountMinor, String currency, String email, Instant now) {
        return jdbc.sql("""
                insert into payment (reference, merchant_id, amount_minor, currency, email, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'INITIATED', ?, ?) returning id""")
                .params(reference, merchantId, amountMinor, currency, email, ts(now), ts(now))
                .query(Long.class).single();
    }

    public Optional<Payment> find(String reference) {
        return jdbc.sql("select " + COLUMNS + " from payment where reference = ?").param(reference).query(ROW).optional();
    }

    /** Locks the row until the transaction ends, so webhooks, verification and refunds apply one at a time. */
    public Optional<Payment> lock(String reference) {
        return jdbc.sql("select " + COLUMNS + " from payment where reference = ? for update").param(reference).query(ROW).optional();
    }

    public void markPending(long id, String provider, String authorizationUrl, Instant now) {
        jdbc.sql("update payment set provider = ?, authorization_url = ?, status = 'PENDING', updated_at = ? where id = ?")
                .params(provider, authorizationUrl, ts(now), id).update();
    }

    public void updateStatus(long id, PaymentStatus status, Instant now) {
        jdbc.sql("update payment set status = ?, updated_at = ? where id = ?").params(status.name(), ts(now), id).update();
    }

    public void addRefund(long id, long refundedMinor, PaymentStatus status, Instant now) {
        jdbc.sql("update payment set refunded_minor = ?, status = ?, updated_at = ? where id = ?")
                .params(refundedMinor, status.name(), ts(now), id).update();
    }

    public void appendEvent(long paymentId, PaymentStatus from, PaymentStatus to, String cause, String providerEventId,
                            String note, Instant now) {
        jdbc.sql("""
                insert into payment_event (payment_id, from_status, to_status, cause, provider_event_id, note, created_at)
                values (?, ?, ?, ?, ?, ?, ?)""")
                .params(paymentId, from == null ? null : from.name(), to.name(), cause, providerEventId, note, ts(now))
                .update();
    }

    public List<PaymentEvent> events(long paymentId) {
        return jdbc.sql("""
                select from_status, to_status, cause, provider_event_id, note, created_at
                from payment_event where payment_id = ? order by id""")
                .param(paymentId)
                .query((rs, n) -> new PaymentEvent(
                        rs.getString("from_status") == null ? null : PaymentStatus.valueOf(rs.getString("from_status")),
                        PaymentStatus.valueOf(rs.getString("to_status")), rs.getString("cause"),
                        rs.getString("provider_event_id"), rs.getString("note"), rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    public void insertRefund(long paymentId, long amountMinor, String providerRefundId, String status, Instant now) {
        jdbc.sql("insert into refund (payment_id, amount_minor, provider_refund_id, status, created_at) values (?, ?, ?, ?, ?)")
                .params(paymentId, amountMinor, providerRefundId, status, ts(now)).update();
    }

    /** Keyset pagination, newest first (topic 5.3). {@code merchantId} null means all merchants (operations). */
    public List<Payment> page(String merchantId, PaymentStatus status, Instant from, Instant to, Cursor after, int limit) {
        StringBuilder sql = new StringBuilder("select " + COLUMNS + " from payment where true");
        var params = new java.util.ArrayList<Object>();
        if (merchantId != null) {
            sql.append(" and merchant_id = ?");
            params.add(merchantId);
        }
        if (status != null) {
            sql.append(" and status = ?");
            params.add(status.name());
        }
        if (from != null) {
            sql.append(" and created_at >= ?");
            params.add(ts(from));
        }
        if (to != null) {
            sql.append(" and created_at < ?");
            params.add(ts(to));
        }
        if (after != null) {
            sql.append(" and (created_at, id) < (?, ?)");
            params.add(ts(after.createdAt()));
            params.add(after.id());
        }
        sql.append(" order by created_at desc, id desc limit ?");
        params.add(limit);
        return jdbc.sql(sql.toString()).params(params).query(ROW).list();
    }

    public List<String> pendingSince(Instant updatedBefore, int limit) {
        return jdbc.sql("select reference from payment where status = 'PENDING' and updated_at < ? order by updated_at limit ?")
                .params(ts(updatedBefore), limit).query(String.class).list();
    }

    /** Records a webhook event id once; returns false if it was already recorded (a duplicate delivery). */
    public boolean recordWebhookEvent(String provider, String eventId, Instant now) {
        return jdbc.sql("insert into webhook_event (provider, event_id, received_at) values (?, ?, ?) on conflict do nothing")
                .params(provider, eventId, ts(now)).update() == 1;
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }
}
