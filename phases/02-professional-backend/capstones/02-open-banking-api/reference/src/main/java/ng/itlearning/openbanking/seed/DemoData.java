package ng.itlearning.openbanking.seed;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Synthetic customers, accounts and transactions (released under CC0). Deterministic for a given seed. */
@Component
public class DemoData {

    private static final String[] FIRST = {"Adaeze", "Babatunde", "Chinedu", "Damilola", "Emeka", "Funmilayo", "Ganiyu",
            "Halima", "Ifeoma", "Jide", "Kemi", "Lanre", "Musa", "Ngozi", "Obinna", "Rukayat", "Segun", "Temitope", "Uche", "Zainab"};
    private static final String[] LAST = {"Adeyemi", "Okafor", "Bello", "Eze", "Ogunleye", "Abubakar", "Nwosu", "Balogun",
            "Ibrahim", "Okeke", "Adebayo", "Danjuma", "Chukwu", "Oladipo"};
    private static final String[] DEBITS = {"POS PURCHASE SHOPRITE LEKKI", "TRANSFER TO %s", "AIRTIME MTN", "DSTV SUBSCRIPTION",
            "IKEDC PREPAID TOKEN", "UBER TRIP", "JUMIA ORDER", "ATM WITHDRAWAL", "LAWMA WASTE BILL"};
    private static final String[] CREDITS = {"SALARY %s LTD", "TRANSFER FROM %s", "REFUND JUMIA", "INTEREST CREDIT"};

    private final JdbcClient jdbc;
    private final Clock clock;

    public DemoData(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Creates one customer with the given account types and {@code txPerAccount} transactions over the past year. */
    @Transactional
    public Customer customer(String username, String fullName, List<String> accountTypes, int txPerAccount, long seed) {
        Random random = new Random(seed);
        long customerId = jdbc.sql("insert into customer (username, full_name) values (?, ?) returning id")
                .params(username, fullName).query(Long.class).single();
        List<String> accountIds = new ArrayList<>();
        Instant now = clock.instant();
        for (String type : accountTypes) {
            String currency = type.equals("DOMICILIARY") ? "USD" : "NGN";
            String id = "acc-" + username + "-" + type.toLowerCase();
            String number = String.format("%010d", Math.floorMod(random.nextLong(), 10_000_000_000L));
            BigDecimal balance = BigDecimal.valueOf(50_000 + random.nextInt(400_000), 0).setScale(2, RoundingMode.HALF_EVEN);
            jdbc.sql("""
                    insert into account (id, customer_id, account_number, account_type, currency, nickname, booked_balance, available_balance)
                    values (?, ?, ?, ?, ?, ?, 0, 0)""")
                    .params(id, customerId, number, type, currency, fullName.split(" ")[0] + " " + type.toLowerCase()).update();
            Instant at = now.minus(Duration.ofDays(360));
            for (int i = 0; i < txPerAccount; i++) {
                at = at.plus(Duration.ofMinutes(1 + random.nextInt((int) Duration.ofDays(360).toMinutes() / Math.max(1, txPerAccount))));
                if (at.isAfter(now)) {
                    break;
                }
                boolean credit = random.nextInt(4) == 0;
                BigDecimal amount = BigDecimal.valueOf(500 + random.nextInt(credit ? 300_000 : 40_000), 0).setScale(2, RoundingMode.HALF_EVEN);
                if (!credit && amount.compareTo(balance) > 0) {
                    credit = true;
                }
                balance = credit ? balance.add(amount) : balance.subtract(amount);
                String other = FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
                String narration = (credit ? CREDITS[random.nextInt(CREDITS.length)] : DEBITS[random.nextInt(DEBITS.length)])
                        .formatted(other.toUpperCase());
                jdbc.sql("""
                        insert into txn (account_id, booked_at, amount, credit_debit, counterparty_name, counterparty_account, narration, balance_after)
                        values (?, ?, ?, ?, ?, ?, ?, ?)""")
                        .params(id, Timestamp.from(at), amount, credit ? "CREDIT" : "DEBIT", other,
                                String.format("%010d", random.nextInt(1_000_000_000)), narration, balance).update();
            }
            jdbc.sql("update account set booked_balance = ?, available_balance = ? where id = ?").params(balance, balance, id).update();
            accountIds.add(id);
        }
        return new Customer(customerId, username, accountIds);
    }

    public static String randomName(Random random) {
        return FIRST[random.nextInt(FIRST.length)] + " " + LAST[random.nextInt(LAST.length)];
    }

    public record Customer(long id, String username, List<String> accountIds) {
    }
}
