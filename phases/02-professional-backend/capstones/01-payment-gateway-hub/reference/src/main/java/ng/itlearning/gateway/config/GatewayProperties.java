package ng.itlearning.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Providers, timeouts and resilience settings (topic 4.4). Secrets come from environment variables only. */
@Validated
@ConfigurationProperties("gateway")
public record GatewayProperties(
        @NotBlank String callbackUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration pendingVerifyAfter,
        @NotNull Duration abandonAfter,
        @NotNull Duration idempotencyTtl,
        @Valid @NotNull CircuitBreaker circuitBreaker,
        @Valid @NotNull Retry verifyRetry,
        @NotEmpty List<@Valid Provider> providers,
        @NotEmpty Map<String, Long> limits) {

    public record Provider(@NotBlank String name, @NotBlank String baseUrl, String secret, int priority,
                           @NotEmpty Set<String> currencies) {
    }

    public record CircuitBreaker(int slidingWindowSize, int minimumNumberOfCalls, float failureRateThreshold,
                                 @NotNull Duration openFor) {
    }

    public record Retry(long maxRetries, @NotNull Duration delay, double multiplier, @NotNull Duration jitter) {
    }
}
