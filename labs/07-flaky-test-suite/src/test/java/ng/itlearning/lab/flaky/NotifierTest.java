package ng.itlearning.lab.flaky;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotifierTest {

    @Test
    void sendsTheTransferAlert() throws InterruptedException {
        Notifier notifier = new Notifier();
        notifier.sendAsync("Debit ₦5,000.00 on 0123****89");
        Thread.sleep(200);                                     // give the background thread time to finish
        assertThat(notifier.sent()).containsExactly("Debit ₦5,000.00 on 0123****89");
    }
}
