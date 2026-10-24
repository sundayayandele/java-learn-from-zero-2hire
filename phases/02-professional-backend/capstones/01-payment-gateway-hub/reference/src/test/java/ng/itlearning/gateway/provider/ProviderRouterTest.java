package ng.itlearning.gateway.provider;

import ng.itlearning.gateway.config.GatewayProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderRouterTest {

    static final GatewayProperties.CircuitBreaker CB = new GatewayProperties.CircuitBreaker(4, 2, 50, Duration.ofMinutes(1));
    static final GatewayProperties.Retry RETRY = new GatewayProperties.Retry(2, Duration.ofMillis(1), 1.0, Duration.ZERO);

    @Test
    void usesTheHighestPriorityProviderThatSupportsTheCurrency() {
        var a = new FakeProvider("a", 1, "NGN");
        var b = new FakeProvider("b", 2, "NGN", "KES");
        var router = new ProviderRouter(List.of(b, a), CB, RETRY);
        assertThat(router.initialise("NGN", p -> p.name()).provider().name()).isEqualTo("a");
        assertThat(router.initialise("KES", p -> p.name()).provider().name()).isEqualTo("b");
    }

    @Test
    void failsOverWhenAProviderIsDownAndOpensItsCircuit() {
        var a = new FakeProvider("a", 1, "NGN");
        a.down = true;
        var b = new FakeProvider("b", 2, "NGN");
        var router = new ProviderRouter(List.of(a, b), CB, RETRY);
        for (int i = 0; i < 3; i++) {
            assertThat(router.initialise("NGN", ProviderRouterTest::fake).provider().name()).isEqualTo("b");
        }
        assertThat(router.states()).containsEntry("a", "OPEN");
        int callsWhileOpen = a.calls.get();
        router.initialise("NGN", ProviderRouterTest::fake);
        assertThat(a.calls.get()).as("an open circuit is not called").isEqualTo(callsWhileOpen);
        router.reset();
        assertThat(router.states()).containsEntry("a", "CLOSED");
    }

    @Test
    void noHealthyProviderMeansNoProviderAvailable() {
        var a = new FakeProvider("a", 1, "NGN");
        a.down = true;
        var router = new ProviderRouter(List.of(a), CB, RETRY);
        assertThatThrownBy(() -> router.initialise("NGN", ProviderRouterTest::fake)).isInstanceOf(NoProviderAvailableException.class);
        assertThatThrownBy(() -> router.initialise("USD", ProviderRouterTest::fake)).isInstanceOf(NoProviderAvailableException.class);
    }

    @Test
    void idempotentCallsAreRetriedUntilTheySucceed() {
        var a = new FakeProvider("a", 1, "NGN");
        a.failFirst = 2;
        var router = new ProviderRouter(List.of(a), new GatewayProperties.CircuitBreaker(10, 10, 50, Duration.ofMinutes(1)), RETRY);
        assertThat(router.idempotentCall("a", ProviderRouterTest::fake)).isEqualTo("ok");
        assertThat(a.calls.get()).isEqualTo(3);
    }

    @Test
    void retriesGiveUpAfterTheConfiguredAttempts() {
        var a = new FakeProvider("a", 1, "NGN");
        a.down = true;
        var router = new ProviderRouter(List.of(a), new GatewayProperties.CircuitBreaker(10, 10, 50, Duration.ofMinutes(1)), RETRY);
        assertThatThrownBy(() -> router.idempotentCall("a", ProviderRouterTest::fake)).isInstanceOf(ProviderUnavailableException.class);
        assertThat(a.calls.get()).isEqualTo(3);
        assertThatThrownBy(() -> router.singleCall("a", ProviderRouterTest::fake)).isInstanceOf(ProviderUnavailableException.class);
        assertThat(router.failureRates()).containsKey("a");
    }

    static String fake(PaymentProvider p) {
        return ((FakeProvider) p).call();
    }

    /** A provider whose behaviour the test controls. */
    static class FakeProvider implements PaymentProvider {
        final String name;
        final int priority;
        final List<String> currencies;
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean down;
        volatile int failFirst;

        FakeProvider(String name, int priority, String... currencies) {
            this.name = name;
            this.priority = priority;
            this.currencies = List.of(currencies);
        }

        String call() {
            int n = calls.incrementAndGet();
            if (down || n <= failFirst) {
                throw new ProviderUnavailableException(name, "simulated");
            }
            return "ok";
        }

        public String name() { return name; }
        public int priority() { return priority; }
        public boolean supports(String currency) { return currencies.contains(currency); }
        public InitResult initialise(InitRequest request) { return new InitResult(call(), "x"); }
        public VerifyResult verify(String reference) { call(); return new VerifyResult(reference, ProviderOutcome.SUCCESS, 1, "NGN"); }
        public RefundResult refund(String reference, long amountMinor) { return new RefundResult(call(), "pending"); }
        public boolean signatureValid(byte[] body, String signature) { return false; }
    }
}
