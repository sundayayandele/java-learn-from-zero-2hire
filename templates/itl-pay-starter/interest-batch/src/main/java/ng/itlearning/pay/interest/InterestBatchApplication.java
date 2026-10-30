package ng.itlearning.pay.interest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Runs the job named on the command line and exits with its status, like any batch program:
 * java -jar interest-batch.jar businessDate=2026-10-30
 */
@SpringBootApplication
public class InterestBatchApplication {

	public static void main(String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(InterestBatchApplication.class, args)));
	}

}
