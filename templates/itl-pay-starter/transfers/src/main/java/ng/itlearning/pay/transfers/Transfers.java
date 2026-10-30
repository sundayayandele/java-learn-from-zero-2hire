package ng.itlearning.pay.transfers;

import java.math.BigDecimal;
import java.util.Optional;
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

    Transfers(JdbcClient jdbc, Outbox outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
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

    Optional<Transfer> find(String reference) {
        return jdbc.sql("SELECT reference, from_account, to_account, amount, state, reason FROM transfer WHERE reference = ?")
                .param(reference).query(Transfer.class).optional();
    }

    /**
     * Moves a transfer from one state to the next and records the event, in one transaction.
     * Returns false if the transfer wasn't in the expected state (another delivery got there first).
     */
    @Transactional
    boolean move(Transfer t, State from, State to, String event, String reason) {
        int changed = jdbc.sql("UPDATE transfer SET state = ?, reason = ?, updated_at = now() WHERE reference = ? AND state = ?")
                .params(to.name(), reason, t.reference(), from.name()).update();
        if (changed == 1) {
            outbox.append(event, t, reason);
        }
        return changed == 1;
    }
}
