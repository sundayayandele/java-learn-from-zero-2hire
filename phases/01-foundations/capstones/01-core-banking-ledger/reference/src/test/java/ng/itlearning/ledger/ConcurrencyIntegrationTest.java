package ng.itlearning.ledger;

import ng.itlearning.ledger.account.Account;
import ng.itlearning.ledger.account.AccountRepository;
import ng.itlearning.ledger.account.AccountNumbers;
import ng.itlearning.ledger.account.InsufficientFundsException;
import ng.itlearning.ledger.journal.LedgerService;
import ng.itlearning.ledger.journal.LedgerService.DepositCommand;
import ng.itlearning.ledger.journal.LedgerService.TransferCommand;
import ng.itlearning.ledger.statement.LedgerQueries;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** 50 transfers fired at once between 10 accounts: money is neither created nor destroyed, and nothing deadlocks. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrencyIntegrationTest {

    @Autowired LedgerService ledger;
    @Autowired AccountRepository accounts;
    @Autowired LedgerQueries queries;

    @Test
    void parallelTransfersPreserveTotals() throws Exception {
        List<String> numbers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String n = ledger.openAccount("Concurrent " + i, "NGN", null).getNumber();
            ledger.deposit(new DepositCommand(n, new BigDecimal("2000.00"), "D-" + UUID.randomUUID(), null));
            numbers.add(n);
        }
        BigDecimal feesBefore = balance(AccountNumbers.FEE_INCOME);
        BigDecimal customersBefore = total(numbers);

        Random random = new Random(42);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 50; i++) {
                int from = random.nextInt(10);
                int to = (from + 1 + random.nextInt(9)) % 10;
                BigDecimal amount = new BigDecimal(100 + random.nextInt(900) + ".00");
                results.add(pool.submit(() -> {
                    start.await();
                    try {
                        ledger.transfer(new TransferCommand(numbers.get(from), numbers.get(to), amount, "C-" + UUID.randomUUID(), null));
                        return true;
                    } catch (InsufficientFundsException expected) {
                        return false;
                    }
                }));
            }
            start.countDown();
        }
        long completed = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) completed++;                        // any unexpected exception (e.g. a deadlock) fails here
        }

        assertThat(completed).isPositive();
        BigDecimal feesCollected = balance(AccountNumbers.FEE_INCOME).subtract(feesBefore);
        assertThat(total(numbers).add(feesCollected)).isEqualByComparingTo(customersBefore);
        assertThat(numbers).allSatisfy(n -> assertThat(balance(n)).isGreaterThanOrEqualTo(BigDecimal.ZERO));
        assertThat(queries.balanceDifferences()).isEmpty();
    }

    private BigDecimal total(List<String> numbers) {
        return numbers.stream().map(this::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal balance(String number) {
        return accounts.findByNumber(number).map(Account::getBalance).orElseThrow();
    }
}
