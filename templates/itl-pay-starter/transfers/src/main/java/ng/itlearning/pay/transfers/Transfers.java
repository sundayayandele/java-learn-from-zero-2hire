package ng.itlearning.pay.transfers;

import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The saga's state. Every change of state and the event announcing it are one local transaction. */
@Component
class Transfers {

    enum State { REQUESTED, RESERVED, CLEARED, REJECTED, FAILED }

    record Transfer(String reference, String fromAccount, String toAccount, BigDecimal amount, State state, String reason) { }

    private final JdbcClient jdbc;
    private final Outbox outbox;
    private final MeterRegistry meters;

    Transfers(JdbcClient jdbc, Outbox outbox, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.meters = meters;
    }

    /** Records a new transfer as REQUESTED, or returns the one that already has this reference. */
    @Transactional
    Transfer requestOrGet(String reference, String from, String to, BigDecimal amount) {
        jdbc.sql("""
                INSERT INTO transfer (reference, from_account, to_account, amount, state)
                VALUES (?, ?, ?, ?, 'REQUESTED') ON CONFLICT (reference) DO NOTHING""")
                .params(reference, from, to, amount).update();
        return find(reference).orElseThrow();
    }

    /**
     * The status a customer's app polls, from Redis when it's there (topic 13.4). Only for answering clients:
     * the saga always reads the database through find(), never a cache that might be a moment old.
     */
    @Cacheable(cacheNames = "transfers", unless = "#result == null")
    public Optional<Transfer> status(String reference) {
        return find(reference);
    }

    Optional<Transfer> find(String reference) {
        return jdbc.sql("SELECT reference, from_account, to_account, amount, state, reason FROM transfer WHERE reference = ?")
                .param(reference).query(Transfer.class).optional();
    }

    /**
     * Moves a transfer from one state to the next and records the event, in one transaction.
     * Returns false if the transfer wasn't in the expected state (another delivery got there first).
     */
    @Transactional
    @CacheEvict(cacheNames = "transfers", key = "#t.reference()")   // after the commit: the cache manager is transaction-aware
    public boolean move(Transfer t, State from, State to, String event, String reason) {
        int changed = jdbc.sql("UPDATE transfer SET state = ?, reason = ?, updated_at = now() WHERE reference = ? AND state = ?")
                .params(to.name(), reason, t.reference(), from.name()).update();
        if (changed == 1) {
            outbox.append(event, t, reason);
            meters.counter("itlpay.transfers", "state", to.name()).increment();   // business metric: transfers reaching each state
        }
        return changed == 1;
    }
}
