package ng.itlearning.lab.flaky;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtpDeliveryTest {

    @Test
    void deliversTheOtpToTheGateway() throws Exception {
        try (FakeSmsGateway gateway = new FakeSmsGateway()) {
            int status = new SmsClient().send("+2348098765432", "OTP 654321");
            Thread.sleep(300);
            assertThat(status).isEqualTo(202);
            assertThat(gateway.received).hasSize(1);
        }
    }
}
