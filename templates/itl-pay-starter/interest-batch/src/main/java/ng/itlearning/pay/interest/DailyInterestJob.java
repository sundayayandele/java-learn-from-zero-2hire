package ng.itlearning.pay.interest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Posts one day's interest to every savings account: read accounts in pages, work out the interest,
 * write postings, 100 accounts per transaction (a chunk). If it fails, a rerun with the same
 * businessDate restarts from the last committed chunk.
 */
@Configuration(proxyBeanMethods = false)
class DailyInterestJob {

    record Account(String accountId, BigDecimal balance, BigDecimal annualRate) { }
    record Posting(String accountId, LocalDate businessDate, BigDecimal amount) { }

    static final int CHUNK = 100;

    @Bean
    Job dailyInterest(JobRepository jobs, Step postInterest) {
        return new JobBuilder("dailyInterest", jobs).start(postInterest).build();
    }

    @Bean
    Step postInterest(JobRepository jobs, PlatformTransactionManager tx, JdbcPagingItemReader<Account> accounts,
                      ItemProcessor<Account, Posting> interest, JdbcBatchItemWriter<Posting> postings) {
        return new StepBuilder("postInterest", jobs)
                .<Account, Posting>chunk(CHUNK)
                .transactionManager(tx)
                .reader(accounts)
                .processor(interest)
                .writer(postings)
                .build();
    }

    /** Pages ordered by a unique key, and saves its position after each chunk: that is what makes the restart work. */
    @Bean
    JdbcPagingItemReader<Account> accounts(DataSource db) throws Exception {
        return new JdbcPagingItemReaderBuilder<Account>()
                .name("accounts")
                .dataSource(db)
                .selectClause("SELECT account_id, balance, annual_rate")
                .fromClause("FROM account_balance")
                .sortKeys(Map.of("account_id", Order.ASCENDING))
                .pageSize(CHUNK)
                .rowMapper(new DataClassRowMapper<>(Account.class))
                .build();
    }

    @Bean
    @StepScope
    ItemProcessor<Account, Posting> interest(@Value("#{jobParameters['businessDate']}") String businessDate,
                                             @Value("${interest.fail-on-account}") String failOn) {
        LocalDate day = LocalDate.parse(businessDate);
        return account -> {
            if (account.accountId().equals(failOn)) {
                throw new IllegalStateException("simulated failure on " + failOn);
            }
            BigDecimal daily = account.balance().multiply(account.annualRate())
                    .divide(BigDecimal.valueOf(day.lengthOfYear()), 2, RoundingMode.HALF_EVEN);   // banker's rounding
            return daily.signum() > 0 ? new Posting(account.accountId(), day, daily) : null;     // null: nothing to post
        };
    }

    /** Idempotent too: if a chunk is ever written twice, the primary key keeps one posting per account per day. */
    @Bean
    JdbcBatchItemWriter<Posting> postings(DataSource db) {
        return new JdbcBatchItemWriterBuilder<Posting>()
                .dataSource(db)
                .sql("""
                        INSERT INTO interest_posting (account_id, business_date, amount)
                        VALUES (:accountId, :businessDate, :amount)
                        ON CONFLICT (account_id, business_date) DO NOTHING""")
                .itemSqlParameterSourceProvider(p -> new MapSqlParameterSource()
                        .addValue("accountId", p.accountId())
                        .addValue("businessDate", p.businessDate())
                        .addValue("amount", p.amount()))
                .build();
    }
}
