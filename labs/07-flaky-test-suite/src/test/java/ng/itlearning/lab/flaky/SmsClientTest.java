package ng.itlearning.lab.flaky;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmsClientTest {

    @Test
    void sendsAnSms() throws Exception {
        try (FakeSmsGateway gateway = new FakeSmsGateway()) {
            assertThat(new SmsClient().send("+2348012345678", "Your OTP is 123456")).isEqualTo(202);
            Thread.sleep(300);                                 // keep the gateway busy like a real call would
            assertThat(gateway.received).containsExactly("+2348012345678|Your OTP is 123456");
        }
    }
}
