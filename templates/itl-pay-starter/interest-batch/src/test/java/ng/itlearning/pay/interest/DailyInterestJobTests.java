package ng.itlearning.pay.interest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.batch.job.enabled=false")
class DailyInterestJobTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired JobOperator operator;
    @Autowired Job dailyInterest;
    @Autowired JdbcClient jdbc;

    static {
        System.setProperty("interest.fail-on-account", "SAV-0457");     // run 1 will fail here
    }

    @AfterAll
    static void clearFailure() {
        System.clearProperty("interest.fail-on-account");
    }

    @Test
    void aFailedRunRestartsFromTheLastCommittedChunkAndNeverPostsTwice() throws Exception {
        JobParameters day = new JobParametersBuilder().addString("businessDate", "2026-10-30").toJobParameters();

        // Run 1 fails on account 457: chunks 1-4 (accounts 1-400) are committed, chunk 5 is rolled back.
        JobExecution first = operator.start(dailyInterest, day);
        assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(postings()).isEqualTo(320);                     // 400 accounts, every fifth earns nothing

        // Fix the cause and run again with the same businessDate: Spring Batch restarts the same job instance.
        System.clearProperty("interest.fail-on-account");
        JobExecution second = operator.start(dailyInterest, day);
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getJobInstance().getInstanceId()).isEqualTo(first.getJobInstance().getInstanceId());
        assertThat(second.getStepExecutions().iterator().next().getReadCount()).isEqualTo(600);   // started at account 401
        assertThat(postings()).isEqualTo(800);

        // A third run for the same day is refused: the day is done.
        assertThatThrownBy(() -> operator.start(dailyInterest, day)).isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    long postings() {
        return jdbc.sql("SELECT count(*) FROM interest_posting WHERE business_date = DATE '2026-10-30'").query(Long.class).single();
    }
}
