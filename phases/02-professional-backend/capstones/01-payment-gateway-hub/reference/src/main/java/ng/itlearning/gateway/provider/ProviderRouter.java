package ng.itlearning.gateway.provider;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import ng.itlearning.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Chooses providers and protects calls to them (ADR 0005): one circuit breaker per provider,
 * failover by priority for initialisation, retries with backoff and jitter for verification only.
 */
public class ProviderRouter {

    private static final Logger log = LoggerFactory.getLogger(ProviderRouter.class);

    private final List<PaymentProvider> providers;
    private final Map<String, CircuitBreaker> breakers = new LinkedHashMap<>();
    private final RetryTemplate verifyRetry;

    public ProviderRouter(List<PaymentProvider> providers, GatewayProperties.CircuitBreaker cb, GatewayProperties.Retry retry) {
        this.providers = providers.stream().sorted(Comparator.comparingInt(PaymentProvider::priority)).toList();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(cb.slidingWindowSize())
                .minimumNumberOfCalls(cb.minimumNumberOfCalls())
                .failureRateThreshold(cb.failureRateThreshold())
                .waitDurationInOpenState(cb.openFor())
                .recordExceptions(ProviderUnavailableException.class)   // 4xx means the provider is healthy
                .build();
        this.providers.forEach(p -> breakers.put(p.name(), CircuitBreaker.of(p.name(), config)));
        this.verifyRetry = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(retry.maxRetries())
                .delay(retry.delay())
                .multiplier(retry.multiplier())
                .jitter(retry.jitter())
                .includes(ProviderUnavailableException.class)
                .build());
    }

    public Optional<PaymentProvider> provider(String name) {
        return providers.stream().filter(p -> p.name().equals(name)).findFirst();
    }

    /**
     * Calls the highest-priority healthy provider for the currency, failing over to the next one when a provider is
     * unavailable. A failed initialisation at one provider is safe to abandon: the customer pays only at the URL we return.
     */
    public <T> Routed<T> initialise(String currency, Function<PaymentProvider, T> call) {
        for (PaymentProvider p : providers) {
            if (!p.supports(currency)) {
                continue;
            }
            CircuitBreaker breaker = breakers.get(p.name());
            try {
                return new Routed<>(p, breaker.executeSupplier(() -> call.apply(p)));
            } catch (CallNotPermittedException e) {
                log.info("Skipping {}: circuit {}", p.name(), breaker.getState());
            } catch (ProviderUnavailableException e) {
                log.warn("Provider {} failed, trying the next one: {}", p.name(), e.getMessage());
            }
        }
        throw new NoProviderAvailableException(currency);
    }

    /** Runs an idempotent call (verify) through the provider's breaker, retrying transient failures with backoff. */
    public <T> T idempotentCall(String providerName, Function<PaymentProvider, T> call) {
        PaymentProvider p = provider(providerName).orElseThrow(() -> new IllegalArgumentException(providerName));
        CircuitBreaker breaker = breakers.get(p.name());
        try {
            return verifyRetry.execute(() -> {
                try {
                    return breaker.executeSupplier(() -> call.apply(p));
                } catch (CallNotPermittedException e) {
                    throw new ProviderUnavailableException(p.name(), "circuit open");
                }
            });
        } catch (RetryException e) {
            if (e.getLastException() instanceof RuntimeException re) {
                throw re;
            }
            throw new ProviderUnavailableException(p.name(), e);
        }
    }

    /** A single call through the breaker without retries (refunds are not retried blindly). */
    public <T> T singleCall(String providerName, Function<PaymentProvider, T> call) {
        PaymentProvider p = provider(providerName).orElseThrow(() -> new IllegalArgumentException(providerName));
        try {
            return breakers.get(p.name()).executeSupplier(() -> call.apply(p));
        } catch (CallNotPermittedException e) {
            throw new ProviderUnavailableException(p.name(), "circuit open");
        }
    }

    public Map<String, String> states() {
        Map<String, String> states = new LinkedHashMap<>();
        breakers.forEach((name, b) -> states.put(name, b.getState().name()));
        return states;
    }

    public Map<String, Float> failureRates() {
        Map<String, Float> rates = new LinkedHashMap<>();
        breakers.forEach((name, b) -> rates.put(name, b.getMetrics().getFailureRate()));
        return rates;
    }

    /** Closes every breaker (operations runbook and tests). */
    public void reset() {
        breakers.values().forEach(CircuitBreaker::reset);
    }

    public record Routed<T>(PaymentProvider provider, T result) {
    }
}
