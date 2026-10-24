package ng.itlearning.gateway.payment;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class IdempotencyRepository {

    private final JdbcClient jdbc;

    public IdempotencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Claims the key for this request. False means another request already holds it. */
    public boolean claim(String merchantId, String key, String requestHash, Instant now, Instant expiresAt) {
        jdbc.sql("delete from idempotency_key where merchant_id = ? and idem_key = ? and expires_at < ?")
                .params(merchantId, key, Timestamp.from(now)).update();
        return jdbc.sql("""
                insert into idempotency_key (merchant_id, idem_key, request_hash, expires_at) values (?, ?, ?, ?)
                on conflict do nothing""")
                .params(merchantId, key, requestHash, Timestamp.from(expiresAt)).update() == 1;
    }

    public Optional<Stored> find(String merchantId, String key) {
        return jdbc.sql("select request_hash, response_json from idempotency_key where merchant_id = ? and idem_key = ?")
                .params(merchantId, key)
                .query((rs, n) -> new Stored(rs.getString("request_hash"), rs.getString("response_json")))
                .optional();
    }

    public void saveResponse(String merchantId, String key, String responseJson) {
        jdbc.sql("update idempotency_key set response_json = ? where merchant_id = ? and idem_key = ?")
                .params(responseJson, merchantId, key).update();
    }

    /** Releases a key whose request failed before anything happened, so the client can retry with it. */
    public void release(String merchantId, String key) {
        jdbc.sql("delete from idempotency_key where merchant_id = ? and idem_key = ?").params(merchantId, key).update();
    }

    public record Stored(String requestHash, String responseJson) {
    }
}
