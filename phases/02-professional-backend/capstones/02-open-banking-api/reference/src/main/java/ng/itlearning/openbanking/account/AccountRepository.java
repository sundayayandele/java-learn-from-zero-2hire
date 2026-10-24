package ng.itlearning.openbanking.account;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class AccountRepository {

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Long> customerIdByUsername(String username) {
        return jdbc.sql("select id from customer where username = ?").param(username).query(Long.class).optional();
    }

    public List<Account> byIds(Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select * from account where id in (:ids) order by id").param("ids", ids)
                .query((rs, n) -> new Account(rs.getString("id"), rs.getLong("customer_id"), rs.getString("account_number"),
                        rs.getString("account_type"), rs.getString("currency"), rs.getString("nickname"),
                        rs.getBigDecimal("booked_balance"), rs.getBigDecimal("available_balance")))
                .list();
    }

    /** True only if every id exists and belongs to the customer. */
    public boolean allOwnedBy(long customerId, Collection<String> ids) {
        Integer owned = jdbc.sql("select count(*) from account where customer_id = :c and id in (:ids)")
                .param("c", customerId).param("ids", ids).query(Integer.class).single();
        return owned == ids.size();
    }

    /** One page of transactions inside [from, to), newest first, after the keyset cursor if given. */
    public List<Transaction> transactions(String accountId, Instant from, Instant to, Instant afterBookedAt, Long afterId, int limit) {
        String sql = """
                select * from txn where account_id = ? and booked_at >= ? and booked_at < ?
                """ + (afterId == null ? "" : " and (booked_at, id) < (?, ?) ") + " order by booked_at desc, id desc limit ?";
        var spec = jdbc.sql(sql).param(accountId).param(Timestamp.from(from)).param(Timestamp.from(to));
        if (afterId != null) {
            spec = spec.param(Timestamp.from(afterBookedAt)).param(afterId);
        }
        return spec.param(limit).query((rs, n) -> new Transaction(rs.getLong("id"), rs.getString("account_id"),
                rs.getTimestamp("booked_at").toInstant(), rs.getBigDecimal("amount"), rs.getString("credit_debit"),
                rs.getString("counterparty_name"), rs.getString("counterparty_account"), rs.getString("narration"),
                rs.getBigDecimal("balance_after"))).list();
    }

    public record Account(String id, long customerId, String number, String type, String currency, String nickname,
                          BigDecimal booked, BigDecimal available) {
    }

    public record Transaction(long id, String accountId, Instant bookedAt, BigDecimal amount, String creditDebit,
                              String counterpartyName, String counterpartyAccount, String narration, BigDecimal balanceAfter) {
    }
}
